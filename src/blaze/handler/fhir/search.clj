(ns blaze.handler.fhir.search
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]))


;; TODO: improve quick hack
(defn- resource-pred [db type {:strs [identifier]}]
  (when identifier
    (let [attr (keyword type "identifier")
          {:db/keys [cardinality]} (util/cached-entity db attr)
          matches?
          (fn [{:Identifier/keys [value]}]
            (= identifier value))]
      (fn [resource]
        (let [value (get resource attr)]
          (if (= :db.cardinality/many cardinality)
            (some matches? value)
            (matches? value)))))))


(defn- entry [base-uri {:strs [resourceType id] :as resource}]
  {:fullUrl (str base-uri "/fhir/" resourceType "/" id)
   :resource resource})


(defn- search [base-uri db type params]
  (let [pred (resource-pred db type params)]
    (cond->
      {:resourceType "Bundle"
       :type "searchset"
       :entry
       (into
         []
         (comp
           (map #(d/entity db (:e %)))
           (filter (or pred (fn [_] true)))
           (map #(pull/pull-resource* db type %))
           (filter #(not (:deleted (meta %))))
           (take 50)
           (map #(entry base-uri %)))
         (d/datoms db :aevt (util/resource-id-attr type)))}

      (nil? pred)
      (assoc :total (util/resource-type-total db type)))))


(defn- handler-intern [base-uri conn]
  (fn [{{:keys [type]} :path-params :keys [params]}]
    (-> (search base-uri (d/db conn) type params)
        (ring/response))))


(s/def :handler.fhir/search fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/search)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-params)
      (wrap-observe-request-duration "search-type")))
