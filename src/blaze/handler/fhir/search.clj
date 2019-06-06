(ns blaze.handler.fhir.search
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]))


;; TODO: improve quick hack
(defn- resource-pred [db type {:strs [identifier]}]
  (if identifier
    (let [attr (keyword type "identifier")
          {:db/keys [cardinality]} (util/cached-entity db attr)
          matches?
          (fn [{:Identifier/keys [value]}]
            (= identifier value))]
      (fn [resource]
        (let [value (get resource attr)]
          (if (= :db.cardinality/many cardinality)
            (some matches? value)
            (matches? value)))))
    (fn [_] true)))


(defn- entry [base-uri {:strs [resourceType id] :as resource}]
  {:fullUrl (str base-uri "/fhir/" resourceType "/" id)
   :resource resource})


(defn- search [base-uri db type query-params]
  {:resourceType "Bundle"
   :type "searchset"
   :entry
   (into
     []
     (comp
       (map #(d/entity db (:e %)))
       (filter (resource-pred db type query-params))
       (map #(pull/pull-resource* db type %))
       (filter #(not (:deleted (meta %))))
       (take 50)
       (map #(entry base-uri %)))
     (d/datoms db :aevt (util/resource-id-attr type)))})


(defn handler-intern [base-uri conn]
  (fn [{{:keys [type]} :route-params :keys [query-params]}]
    (let [db (d/db conn)]
      (if (util/cached-entity db (keyword type))
        (ring/response (search base-uri db type query-params))
        (handler-util/error-response
          {::anom/category ::anom/not-found
           :fhir/issue "not-found"})))))


(s/def :handler.fhir/search fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/search)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-params)
      (wrap-exception)
      (wrap-json)
      (wrap-observe-request-duration "search-type")))
