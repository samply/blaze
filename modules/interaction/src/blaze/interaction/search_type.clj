(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.string :as str]
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


(defn- summary?
  "Returns true iff a summary result is requested."
  [{summary "_summary" :as query-params}]
  (or (zero? (fhir-util/page-size query-params)) (= "count" summary)))


(defn- entries
  [router db type {:keys [clauses page-size]}]
  (when-ok [entries (if (empty? clauses)
                      (d/list-resources db type)
                      (d/type-query db type clauses))]
    (into
      []
      (comp
        (take (inc page-size))
        (map #(entry router %)))
      entries)))


#_(defn- decode-sort-params [db type sort-params]
    (transduce
      (map
        (fn [sort-param]
          (if (str/starts-with? sort-param "-")
            {:order :desc
             :code (subs sort-param 1)}
            {:order :asc
             :code sort-param})))
      (completing
        (fn [res {:keys [code] :as sort-param}]
          (if-let [search-param (db/find-search-param-by-type-and-code db type code)]
            (conj res (assoc sort-param :search-param search-param))
            (reduced
              {::anom/category ::anom/incorrect
               ::anom/message
               (format "Unknown sort parameter with code `%s` on type `%s`."
                       code type)
               :fhir/issue "value"
               :fhir/operation-outcome "MSG_SORT_UNKNOWN"}))))
      []
      (str/split sort-params #",")))


(defn- clauses [params]
  (into [] (remove (fn [[k]] (and (str/starts-with? k "_") (not= k "_id")))) params))


(defn- decode-params
  [_ _ params]
  {:clauses (clauses params)
   :summary? (summary? params)
   :page-size (fhir-util/page-size params)}
  #_(let [sort (some->> sort-params (decode-sort-params db type))]
      (if (::anom/category sort)
        sort
        {:pred (resource-pred db type params)
         :sort sort
         :summary? (summary? params)
         :page-size (fhir-util/page-size params)})))


(defn- search [router db type params]
  (when-ok [params (decode-params db type params)]
    (if (:summary? params)
      (cond->
        {:resourceType "Bundle"
         :type "searchset"}

        (empty? (:clauses params))
        (assoc :total (d/type-total db type)))
      (when-ok [entries (entries router db type params)]
        (let [page-size (:page-size params)]
          (cond->
            {:resourceType "Bundle"
             :type "searchset"}

            (empty? (:clauses params))
            (assoc :total (d/type-total db type))

            (<= (count entries) page-size)
            (assoc :total (count entries))

            true
            (assoc :entry (take page-size entries))))))))


(defn- handler-intern [node]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [params]
        ::reitit/keys [router]}]
    (log/debug
      (if (seq params)
        (format "GET [base]/%s?%s" type
                (->> (map (fn [[k v]] (format "%s=%s"k v)) params)
                     (str/join "&")))
        (format "GET [base]/%s" type)))
    (let [body (search router (d/db node) type params)]
      (if (::anom/category body)
        (handler-util/error-response body)
        (ring/response body)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-params)
      (wrap-observe-request-duration "search-type")))


(defmethod ig/init-key :blaze.interaction/search-type
  [_ {:keys [node]}]
  (log/info "Init FHIR search-type interaction handler")
  (handler node))
