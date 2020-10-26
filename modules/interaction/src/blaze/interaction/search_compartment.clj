(ns blaze.interaction.search-compartment
  (:require
    [blaze.anomaly :refer [ex-anom when-ok]]
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params :as params]
    [blaze.interaction.search.util :as search-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.uuid :refer [random-uuid]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- handles-and-clauses
  [{:keys [code id type] :preference/keys [handling] :or {handling "strict"}
    {:keys [clauses]} :params}
   db]
  (cond
    (empty? clauses)
    {:handles (into [] (d/list-compartment-resource-handles db code id type))}

    (= "strict" handling)
    (when-ok [handles (d/compartment-query db code id type clauses)]
      {:handles (into [] handles)
       :clauses clauses})

    :else
    (let [query (d/compile-compartment-query-lenient db code type clauses)]
      {:handles (into [] (d/execute-query db query id))
       :clauses (d/query-clauses query)})))


(defn- entries
  [{:keys [router] {:keys [page-offset page-size]} :params} resources]
  (into
    []
    (comp
      (drop page-offset)
      (take (inc page-size))
      (map #(search-util/entry router %)))
    resources))


(defn- self-link
  [{:keys [match] {:keys [page-offset] :as params} :params} clauses t]
  {:fhir/type :fhir.Bundle/link
   :relation "self"
   :url (type/->Uri (nav/url match params clauses t
                             {"__page-offset" page-offset}))})


(defn- next-link-offset [{:keys [page-offset]} entries]
  {"__page-offset" (+ page-offset (dec (count entries)))})


(defn- next-link [{:keys [match params]} clauses t entries]
  {:fhir/type :fhir.Bundle/link
   :relation "next"
   :url (type/->Uri (nav/url match params clauses t
                             (next-link-offset params entries)))})


(defn- search-normal
  [{{:keys [page-size]} :params :as context} db]
  (let [t (or (d/as-of-t db) (d/basis-t db))
        {:keys [handles clauses] :as handles-and-clauses}
        (handles-and-clauses context db)]
    (if (::anom/category handles-and-clauses)
      (ac/failed-future (ex-anom handles-and-clauses))
      (-> (d/pull-many db handles)
          (ac/then-apply
            (fn [resources]
              (let [entries (entries context resources)]
                (cond->
                  {:fhir/type :fhir/Bundle
                   :id (str (random-uuid))
                   :type #fhir/code"searchset"
                   :total (type/->UnsignedInt (count handles))
                   :entry (take page-size entries)
                   :link [(self-link context clauses t)]}

                  (< page-size (count entries))
                  (update :link conj (next-link context clauses t
                                                entries))))))))))


(defn- search-summary [context db]
  (let [t (or (d/as-of-t db) (d/basis-t db))
        {:keys [handles clauses] :as handles-and-clauses}
        (handles-and-clauses context db)]
    (if (::anom/category handles-and-clauses)
      (ac/failed-future (ex-anom handles-and-clauses))
      (ac/completed-future
        {:fhir/type :fhir/Bundle
         :id (str (random-uuid))
         :type #fhir/code"searchset"
         :total (type/->UnsignedInt (count handles))
         :link [(self-link context clauses t)]}))))


(defn- search [{:keys [params] :as context} db]
  (if (:summary? params)
    (search-summary context db)
    (search-normal context db)))


(defn- context [router match code id type headers params]
  (cond
    (not (s/valid? :blaze.resource/id id))
    {::anom/category ::anom/incorrect
     ::anom/message (format "The identifier `%s` is invalid." id)
     :fhir/issue "value"}

    (not (s/valid? :fhir.type/name type))
    {::anom/category ::anom/incorrect
     ::anom/message (format "The type `%s` is invalid." type)
     :fhir/issue "value"}

    :else
    (when-ok [params (params/decode params)]
      {:router router
       :match match
       :code code
       :id id
       :type type
       :preference/handling (handler-util/preference headers "handling")
       :params params})))


(defn- handler-intern [node]
  (fn [{{{:fhir.compartment/keys [code]} :data :as match} ::reitit/match
        {:keys [id type]} :path-params
        :keys [headers params]
        ::reitit/keys [router]}]
    (let [context (context router match code id type headers params)]
      (if (::anom/category context)
        (ac/completed-future (handler-util/error-response context))
        (-> (handler-util/db node (fhir-util/t params))
            (ac/then-compose #(search context %))
            (ac/then-apply ring/response)
            (ac/exceptionally handler-util/error-response))))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-observe-request-duration "search-compartment")))


(defmethod ig/init-key :blaze.interaction/search-compartment
  [_ {:keys [node]}]
  (log/info "Init FHIR search-compartment interaction handler")
  (handler node))
