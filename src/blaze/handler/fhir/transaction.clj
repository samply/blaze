(ns blaze.handler.fhir.transaction
  "FHIR batch/transaction interaction.

  https://www.hl7.org/fhir/http.html#transaction"
  (:require
    [blaze.bundle :as bundle]
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.util :as handler-fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- validate-entry
  {:arglists '([db entry])}
  [db {:strs [resource] {:strs [method url]} "request" :as entry}]
  (let [[type id] (handler-fhir-util/extract-type-and-id url)]
    (cond
      (not (#{"GET" "HEAD" "POST" "PUT" "DELETE" "PATCH"} method))
      {::anom/category ::anom/incorrect
       :fhir/issue "code-invalid"}

      (not (#{"POST" "PUT" "DELETE"} method))
      {::anom/category ::anom/unsupported
       :fhir/issue "not-supported"}

      (nil? (util/cached-entity db (keyword type)))
      {::anom/category ::anom/incorrect
       ::anom/message (str "Unknown type `" type "`.")
       :fhir/issue "value"}

      (not (map? resource))
      {::anom/category ::anom/incorrect
       :fhir/issue "structure"
       :fhir/operation-outcome "MSG_JSON_OBJECT"}

      (not= type (get resource "resourceType"))
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"}

      (and (= "PUT" method) (not= id (get resource "id")))
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"}

      :else
      entry)))


(defmulti validate-entries (fn [_ type _] type))


(defmethod validate-entries "batch"
  [db _ entries]
  (mapv #(validate-entry db %) entries))


(defmethod validate-entries "transaction"
  [db _ entries]
  (reduce
    (fn [res entry]
      (let [entry (validate-entry db entry)]
        (if (::anom/category entry)
          (reduced entry)
          (conj res entry))))
    []
    entries))


(defn- prepare-entry
  [db {{:strs [method]} "request" :strs [resource] :as entry}]
  (cond
    (= "PUT" method)
    (let [{type "resourceType" id "id"} resource]
      (if-let [resource (util/resource db type id)]
        (assoc entry :blaze/old-resource resource)
        entry))

    (= "POST" method)
    (assoc-in entry ["resource" "id"] (str (d/squuid)))

    :else
    entry))


(defn validate-and-prepare-bundle
  [db {:strs [resourceType type entry]}]
  (cond
    (not= "Bundle" resourceType)
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "value"})

    (not (#{"batch" "transaction"} type))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "value"})

    :else
    (let [entries (validate-entries db type entry)]
      (if (::anom/category entries)
        (md/error-deferred entries)
        (mapv #(prepare-entry db %) entries)))))


(defmulti upsert-entry
  {:arglists '([conn db entry])}
  (fn [_ _ {{:strs [method]} "request"}] method))


(defmethod upsert-entry "POST"
  [conn db {:strs [resource]}]
  (handler-fhir-util/upsert-resource conn db 0 resource))


(defmethod upsert-entry "PUT"
  [conn db {:strs [resource]}]
  (handler-fhir-util/upsert-resource conn db 1 resource))


(defmulti transact
  {:arglists '([conn db type prepared-entries])}
  (fn [_ _ type _] type))


(defmethod transact "batch" [conn db _ entries]
  (mapv #(assoc % :blaze/tx-result (upsert-entry conn db %)) entries))


(defmethod transact "transaction" [conn db _ entries]
  (let [tx-result (tx/transact-async conn (bundle/tx-data db entries))]
    (mapv #(assoc % :blaze/tx-result tx-result) entries)))


(defn- build-entry
  [base-uri
   {{type "resourceType" id "id"} "resource" {db :db-after} :blaze/tx-result
    :blaze/keys [old-resource] :as entry}]
  (let [last-modified (util/tx-instant (util/basis-transaction db))
        versionId (d/basis-t db)
        response
        (cond->
          {:status (str (if old-resource 200 201))
           :etag (str "W/\"" versionId "\"")
           :lastModified (str last-modified)}
          (nil? old-resource)
          (assoc :location (str base-uri "/fhir/" type "/" id "/_history/" versionId)))]
    (-> entry
        (assoc :response response)
        (dissoc :blaze/tx-result))))


(defn- build-response [base-uri bundle-type entries]
  (ring/response
    {:resourceType "Bundle"
     :type (str bundle-type "-response")
     :entry (mapv #(build-entry base-uri %) entries)}))


(defn handler-intern [base-uri conn]
  (fn [{{:strs [type] :as bundle} :body}]
    (log/trace bundle)
    (let [db (d/db conn)]
      (-> (validate-and-prepare-bundle db bundle)
          (md/chain' #(transact conn db type %))
          (md/chain' #(build-response base-uri type %))
          (md/catch' handler-util/error-response)))))


(s/def :handler.fhir/transaction fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/transaction)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-exception)
      (wrap-json)))
