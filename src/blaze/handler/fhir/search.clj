(ns blaze.handler.fhir.search
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [ring.util.response :as ring]))


(defn entry [base-uri {:strs [resourceType id] :as resource}]
  {:fullUrl (str base-uri "/fhir/" resourceType "/" id)
   :resource resource})


(defn search [base-uri db type]
  {:resourceType "Bundle"
   :type "searchset"
   :entry
   (into
     []
     (comp
       (map #(pull/pull-resource db type (:v %)))
       (filter #(not (:deleted (meta %))))
       (take 10)
       (map #(entry base-uri %)))
     (d/datoms db :aevt (util/resource-id-attr type)))})


(defn handler-intern [base-uri conn]
  (fn [{{:keys [type]} :route-params}]
    (let [db (d/db conn)]
      (if (util/cached-entity db (keyword type))
        (ring/response (search base-uri db type))
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
      (wrap-json)
      (wrap-exception)))
