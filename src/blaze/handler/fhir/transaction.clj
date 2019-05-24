(ns blaze.handler.fhir.transaction
  "FHIR batch/transaction interaction.

  https://www.hl7.org/fhir/http.html#transaction"
  (:require
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

      (not (#{"PUT" "POST"} method))
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
  [{{:strs [method]} "request" :as entry}]
  (if (= "POST" method)
    (assoc-in entry ["resource" "id"] (str (d/squuid)))
    entry))


(defn prepare-bundle [db {:strs [resourceType type entry]}]
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
        (mapv prepare-entry entries)))))


(defmulti transact (fn [_ type _] type))


(defmethod transact "batch" [conn _ entries]
  (mapv
    (fn [{:strs [resource] :as entry}]
      (assoc entry :blaze/tx-result (handler-fhir-util/update-resource conn resource)))
    entries))


(defmethod transact "transaction" [conn _ entries]
  (let [max-retries 5
        entries (tx/resolve-entry-links (d/db conn) entries)]
    (md/loop [retried 0
              db (d/db conn)]
      (-> (tx/transact-async conn (into [] (mapcat #(tx/resource-update db (get % "resource"))) entries))
          (md/chain'
            (fn [tx-result]
              (mapv #(assoc % :blaze/tx-result tx-result) entries)))
          (md/catch'
            (fn [{::anom/keys [category] :as anomaly}]
              (if (and (< retried max-retries) (= ::anom/conflict category))
                (-> (d/sync conn (inc (d/basis-t db)))
                    (md/chain #(md/recur (inc retried) %)))
                (md/error-deferred anomaly))))))))


(defn- entry-response
  [base-uri
   {{type "resourceType" id "id"} "resource" {db :db-after} :blaze/tx-result
    :as entry}]
  (let [{:keys [version]} (d/entity db [(keyword type "id") id])
        last-modified (util/tx-instant (util/basis-transaction db))
        versionId (d/basis-t db)
        response
        (cond->
          {:status (str (if (zero? version) 201 200))
           :etag (str "W/\"" versionId "\"")
           :lastModified (str last-modified)}
          (zero? version)
          (assoc :location (str base-uri "/fhir/" type "/" id "/_history/" versionId)))]
    (-> entry
        (assoc :response response)
        (dissoc :blaze/tx-result))))


(defn- build-response [base-uri bundle-type entries]
  (ring/response
    {:resourceType "Bundle"
     :type (str bundle-type "-response")
     :entry (mapv #(entry-response base-uri %) entries)}))


(defn handler-intern [base-uri conn]
  (fn [{{:strs [type] :as bundle} :body}]
    (log/trace bundle)
    (-> (prepare-bundle (d/db conn) bundle)
        (md/chain' #(transact conn type %))
        (md/chain' #(build-response base-uri type %))
        (md/catch' handler-util/error-response))))


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
