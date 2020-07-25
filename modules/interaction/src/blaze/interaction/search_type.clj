(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search

  Pagination is implemented in two different ways. If no search parameters are
  given, the id of the first resource of the next page is passed in __page-id
  and used as start-id in `d/list-resources`. If on the other hand search
  parameters are given, `d/type-query` is used which doesn't offer the
  possibility to specify start arguments. So in this case a simple offset is
  used in __page-offset which is used to drop the resources of previous pages.

  In case performance gets a problem here, we have to add start arguments to
  `d/type-query`."
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.async-comp :as ac]
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as util]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params :as params]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
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


(defn- entries
  "Returns bundle entries."
  [router db type {:keys [clauses page-id page-offset page-size]}]
  (when-ok [entries (if (empty? clauses)
                      (d/list-resources db type page-id)
                      (d/type-query db type clauses))]
    (into
      []
      (comp
        (drop page-offset)
        (take (inc page-size))
        (map #(entry router %)))
      entries)))


(defn- self-link-offset [{:keys [clauses page-offset]} entries]
  (if (seq clauses)
    {"__page-offset" page-offset}
    {"__page-id" (-> entries first :resource :id)}))


(defn- self-link [match params t entries]
  {:relation "self"
   :url (nav/url match params t (self-link-offset params entries))})


(defn- next-link-offset [{:keys [clauses page-offset]} entries]
  (if (seq clauses)
    {"__page-offset" (+ page-offset (dec (count entries)))}
    {"__page-id" (-> entries peek :resource :id)}))


(defn- next-link [match params t entries]
  {:relation "next"
   :url (nav/url match params t (next-link-offset params entries))})


(defn- total
  "Calculates the total number of resources returned.

  If we have no clauses (returning all resources), we can use `d/type-total`.
  Secondly, if the number of entries found is not more than one page in size,
  we can use that number. Otherwise there is no cheap way to calculate the
  number of matching resources, so we don't report it."
  [db type {:keys [clauses page-size page-offset]} entries]
  (cond
    (empty? clauses)
    (d/type-total db type)

    (and (zero? page-offset) (<= (count entries) page-size))
    (count entries)))


(defn- search** [router match db type params]
  (let [t (or (d/as-of-t db) (d/basis-t db))]
    (when-ok [entries (entries router db type params)]
      (let [page-size (:page-size params)
            total (total db type params entries)]
        (cond->
          {:resourceType "Bundle"
           :type "searchset"
           :entry (take page-size entries)}

          total
          (assoc :total total)

          (seq entries)
          (update :link (fnil conj []) (self-link match params t entries))

          (< page-size (count entries))
          (update :link (fnil conj []) (next-link match params t entries)))))))


(defn- summary-total [db type {:keys [clauses]}]
  (if (empty? clauses)
    (d/type-total db type)
    (transduce (map (constantly 1)) + 0 (d/type-query db type clauses))))


(defn- search-summary [db type params]
  {:resourceType "Bundle"
   :type "searchset"
   :total (summary-total db type params)})


(defn- search* [router match db type params]
  (if (:summary? params)
    (search-summary db type params)
    (search** router match db type params)))


(defn- search [router match db type params]
  (when-ok [params (params/decode params)]
    (search* router match db type params)))


(defn- handle [router match db type params]
  (let [body (search router match db type params)]
    (if (::anom/category body)
      (util/error-response body)
      (ring/response body))))


(defn- handler-intern [node]
  (fn [{{{:fhir.resource/keys [type]} :data :as match} ::reitit/match
        :keys [params]
        ::reitit/keys [router]}]
    (-> (util/db node (fhir-util/t params))
        (ac/then-apply #(handle router match % type params)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-params)
      (wrap-observe-request-duration "search-type")))


(defmethod ig/init-key :blaze.interaction/search-type
  [_ {:keys [node]}]
  (log/info "Init FHIR search-type interaction handler")
  (handler node))
