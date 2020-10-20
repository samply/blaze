(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.anomaly :refer [ex-anom when-ok]]
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params :as params]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.uuid :refer [random-uuid]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- entry
  [router {:fhir/keys [type] :keys [id] :as resource}]
  {:fullUrl (type/->Uri (fhir-util/instance-url router (name type) id))
   :resource resource
   :search {:fhir/type :fhir.Bundle.entry/search :mode #fhir/code"match"}})


(defn- handles-and-clauses
  [{:keys [type] :preference/keys [handling] :or {handling "strict"}
    {:keys [clauses page-id]} :params}
   db]
  (cond
    (empty? clauses)
    {:handles (d/list-resource-handles db type page-id)}

    (= "strict" handling)
    (when-ok [handles (d/type-query db type clauses page-id)]
      {:handles handles
       :clauses clauses})

    :else
    (let [query (d/compile-type-query-lenient db type clauses)]
      {:handles (d/execute-query db query page-id)
       :clauses (d/query-clauses query)})))


(defn- entries-and-clauses
  "Returns a CompletableFuture that will complete with a vector of all entries
  of the current page plus one entry of the next page."
  [{:keys [router] {:keys [page-size]} :params :as context} db]
  (let [{:keys [handles clauses] :as handles-and-clauses}
        (handles-and-clauses context db)]
    (if (::anom/category handles-and-clauses)
      (ac/failed-future (ex-anom handles-and-clauses))
      (-> (d/pull-many db (into [] (take (inc page-size)) handles))
          (ac/then-apply
            (fn [resources]
              {:entries (mapv #(entry router %) resources)
               :clauses clauses}))))))


(defn- self-link-offset [entries]
  (when-let [id (-> entries first :resource :id)]
    {"__page-id" id}))


(defn- self-link [{:keys [match params]} clauses t entries]
  {:fhir/type :fhir.Bundle/link
   :relation "self"
   :url (type/->Uri (nav/url match params clauses t (self-link-offset entries)))})


(defn- next-link-offset [entries]
  {"__page-id" (-> entries peek :resource :id)})


(defn- next-link [{:keys [match params]} clauses t entries]
  {:fhir/type :fhir.Bundle/link
   :relation "next"
   :url (type/->Uri (nav/url match params clauses t (next-link-offset entries)))})


(defn- total
  "Calculates the total number of resources returned.

  If we have no clauses (returning all resources), we can use `d/type-total`.
  Secondly, if the number of entries found is not more than one page in size,
  we can use that number. Otherwise there is no cheap way to calculate the
  number of matching resources, so we don't report it."
  [db type {:keys [clauses page-size page-id]} entries]
  (cond
    (empty? clauses)
    (d/type-total db type)

    (and (nil? page-id) (<= (count entries) page-size))
    (count entries)))


(defn- search-normal [{:keys [type params] :as context} db]
  (let [t (or (d/as-of-t db) (d/basis-t db))]
    (-> (entries-and-clauses context db)
        (ac/then-apply
          (fn [{:keys [entries clauses]}]
            (let [page-size (:page-size params)
                  total (total db type params entries)]
              (cond->
                {:fhir/type :fhir/Bundle
                 :id (str (random-uuid))
                 :type #fhir/code"searchset"
                 :entry (take page-size entries)
                 :link [(self-link context clauses t entries)]}

                total
                (assoc :total (type/->UnsignedInt total))

                (< page-size (count entries))
                (update :link conj (next-link context clauses t entries)))))))))


(defn- summary-total
  [{:keys [type] :preference/keys [handling] :or {handling "strict"}
    {:keys [clauses]} :params}
   db]
  (cond
    (empty? clauses)
    {:total (d/type-total db type)}

    (= "strict" handling)
    (when-ok [handles (d/type-query db type clauses)]
      {:total (transduce (map (constantly 1)) + 0 handles)
       :clauses clauses})

    :else
    (let [query (d/compile-type-query-lenient db type clauses)]
      {:total (transduce (map (constantly 1)) + 0 (d/execute-query db query))
       :clauses (d/query-clauses query)})))


(defn- search-summary [context db]
  (let [t (or (d/as-of-t db) (d/basis-t db))
        {:keys [total clauses] :as summary-total} (summary-total context db)]
    (if (::anom/category summary-total)
      (ac/failed-future (ex-anom summary-total))
      (ac/completed-future
        {:fhir/type :fhir/Bundle
         :id (str (random-uuid))
         :type #fhir/code"searchset"
         :total (type/->UnsignedInt total)
         :link [(self-link context clauses t [])]}))))


(defn- search [{:keys [params] :as context} db]
  (if (:summary? params)
    (search-summary context db)
    (search-normal context db)))


(defn- context [router match type headers params]
  (when-ok [params (params/decode params)]
    {:router router
     :match match
     :type type
     :preference/handling (handler-util/preference headers "handling")
     :params params}))


(defn- handler-intern [node]
  (fn [{{{:fhir.resource/keys [type]} :data :as match} ::reitit/match
        :keys [headers params]
        ::reitit/keys [router]}]
    (let [context (context router match type headers params)]
      (if (::anom/category context)
        (ac/completed-future (handler-util/error-response context))
        (-> (handler-util/db node (fhir-util/t params))
            (ac/then-compose #(search context %))
            (ac/then-apply ring/response)
            (ac/exceptionally handler-util/error-response))))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-observe-request-duration "search-type")))


(defmethod ig/init-key :blaze.interaction/search-type
  [_ {:keys [node]}]
  (log/info "Init FHIR search-type interaction handler")
  (handler node))
