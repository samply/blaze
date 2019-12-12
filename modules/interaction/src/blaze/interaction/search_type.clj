(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as db]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- normalize [s]
  (-> s str/trim str/lower-case))


;; TODO: improve quick hack
(defn- resource-pred
  [db type {:strs [identifier title title:contains measure url]}]
  (cond
    identifier
    (let [attr (keyword type "identifier")
          {:db/keys [cardinality]} (db/cached-entity db attr)
          matches?
          (fn [{:Identifier/keys [value]}]
            (= identifier value))]
      (fn [resource]
        (let [value (get resource attr)]
          (if (= :db.cardinality/many cardinality)
            (some matches? value)
            (matches? value)))))

    (and (#{"Library" "Measure"} type) title)
    (let [title (normalize title)]
      (fn [resource]
        (when-let [value ((keyword type "title") resource)]
          (str/starts-with? (normalize value) title))))

    (and (#{"Library" "Measure"} type) title:contains)
    (let [title (normalize title:contains)]
      (fn [resource]
        (when-let [value ((keyword type "title") resource)]
          (str/includes? (normalize value) title))))

    (and (#{"MeasureReport"} type) measure)
    (fn [resource]
      (when-let [value ((keyword type "measure") resource)]
        (= value measure)))

    (and (#{"Library" "Measure"} type) url)
    (fn [resource]
      (when-let [value ((keyword type "url") resource)]
        (= value url)))))


(defn- entry
  [router {type "resourceType" id "id" :as resource}]
  {:fullUrl (fhir-util/instance-url router type id)
   :resource resource
   :search {:mode "match"}})


(defn- summary?
  "Returns true iff a summary result is requested."
  [{summary "_summary" :as query-params}]
  (or (zero? (fhir-util/page-size query-params)) (= "count" summary)))


(defn- entries
  [router db type {:keys [pred sort page-size]}]
  (if (seq sort)

    (let [[{:keys [search-param order]}] sort]
      (into
        []
        (comp
          (filter (or pred any?))
          (map #(pull/pull-resource* db type %))
          (take page-size)
          (map #(entry router %)))
        (cond-> (db/list-resources-sorted-by db type search-param)
          (= :desc order) reverse)))

    (into
      []
      (comp
        (filter (or pred any?))
        (map #(pull/pull-resource* db type %))
        (take page-size)
        (map #(entry router %)))
      (db/list-resources db type))))


(defn- decode-sort-params [db type sort-params]
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


(defn- decode-params
  [db type {sort-params "_sort" :as params}]
  (let [sort (some->> sort-params (decode-sort-params db type))]
    (if (::anom/category sort)
      sort
      {:pred (resource-pred db type params)
       :sort sort
       :summary? (summary? params)
       :page-size (fhir-util/page-size params)})))


(defn- search [router db type params]
  (let [params (decode-params db type params)]
    (if (::anom/category params)
      params
      (cond->
        {:resourceType "Bundle"
         :type "searchset"}

        (nil? (:pred params))
        (assoc :total (db/type-total db type))

        (not (:summary? params))
        (assoc :entry (entries router db type params))))))


(defn- handler-intern [conn]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [params]
        ::reitit/keys [router]}]
    (let [body (search router (d/db conn) type params)]
      (if (::anom/category body)
        (handler-util/error-response body)
        (ring/response body)))))


(s/def :handler.fhir/search fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn)
  :ret :handler.fhir/search)

(defn handler
  ""
  [conn]
  (-> (handler-intern conn)
      (wrap-params)
      (wrap-observe-request-duration "search-type")))


(defmethod ig/init-key :blaze.interaction/search-type
  [_ {:database/keys [conn]}]
  (log/info "Init FHIR search-type interaction handler")
  (handler conn))
