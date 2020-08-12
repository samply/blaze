(ns blaze.interaction.search-compartment
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.async-comp :as ac]
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as util]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params :as params]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- entry
  [router {type :resourceType id :id :as resource}]
  {:fullUrl (fhir-util/instance-url router type id)
   :resource resource
   :search {:mode "match"}})


(defn- resource-handles [db code id type {:keys [clauses]}]
  (if (empty? clauses)
    (into [] (d/list-compartment-resource-handles db code id type))
    (when-ok [handles (d/compartment-query db code id type clauses)]
      (into [] handles))))


(defn- entries [router {:keys [page-offset page-size]} resources]
  (into
    []
    (comp
      (drop page-offset)
      (take (inc page-size))
      (map #(entry router %)))
    resources))


(defn- self-link [match {:keys [page-offset] :as params} t]
  {:relation "self"
   :url (nav/url match params t {"__page-offset" page-offset})})


(defn- next-link-offset [{:keys [page-offset]} entries]
  {"__page-offset" (+ page-offset (dec (count entries)))})


(defn- next-link [match params t entries]
  {:relation "next"
   :url (nav/url match params t (next-link-offset params entries))})


(defn- search*
  [router match db code id type {:keys [summary? page-size] :as params}]
  (let [t (or (d/as-of-t db) (d/basis-t db))]
    (when-ok [handles (resource-handles db code id type params)]
      (if summary?
        (ac/completed-future
          {:resourceType "Bundle"
           :type "searchset"
           :total (count handles)})
        (-> (d/pull-many db handles)
            (ac/then-apply
              (fn [resources]
                (let [entries (entries router params resources)]
                  (cond->
                    {:resourceType "Bundle"
                     :type "searchset"
                     :total (count handles)
                     :entry (take page-size entries)}

                    (seq entries)
                    (update :link (fnil conj []) (self-link match params t))

                    (< page-size (count entries))
                    (update :link (fnil conj []) (next-link match params t entries)))))))))))


(defn- search [router match db code id type params]
  (when-ok [params (params/decode params)]
    (search* router match db code id type params)))


(defn- handle [router match db code id type params]
  (let [body (search router match db code id type params)]
    (if (::anom/category body)
      (ac/completed-future (util/error-response body))
      (ac/then-apply body ring/response))))


(defn- handler-intern [node]
  (fn [{{{:fhir.compartment/keys [code]} :data :as match} ::reitit/match
        {:keys [id type]} :path-params
        :keys [params]
        ::reitit/keys [router]}]
    (cond
      (not (s2/valid? :fhir/id id))
      (ac/completed-future
        (util/error-response
          {::anom/category ::anom/incorrect
           ::anom/message (format "The identifier `%s` is invalid." id)
           :fhir/issue "value"}))

      (not (s/valid? :blaze.resource/resourceType type))
      (ac/completed-future
        (util/error-response
          {::anom/category ::anom/incorrect
           ::anom/message (format "The type `%s` is invalid." type)
           :fhir/issue "value"}))

      :else
      (-> (util/db node (fhir-util/t params))
          (ac/then-compose #(handle router match % code id type params))))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-params)
      (wrap-observe-request-duration "search-compartment")))


(defmethod ig/init-key :blaze.interaction/search-compartment
  [_ {:keys [node]}]
  (log/info "Init FHIR search-compartment interaction handler")
  (handler node))
