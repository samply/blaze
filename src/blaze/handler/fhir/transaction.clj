(ns blaze.handler.fhir.transaction
  "FHIR batch/transaction interaction.

  https://www.hl7.org/fhir/http.html#transaction"
  (:require
    [blaze.bundle :as bundle]
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.util :as handler-fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [ring.util.response :as ring]))


(defn- validate-entry
  {:arglists '([db entry])}
  [db {:strs [resource] {:strs [method url]} "request" :as entry}]
  (let [[type id] (bundle/match-url url)]
    (cond
      (not (#{"GET" "HEAD" "POST" "PUT" "DELETE" "PATCH"} method))
      {::anom/category ::anom/incorrect
       ::anom/message (str "Unknown method `" method "`.")
       :fhir/issue "value"}

      (not (#{"POST" "PUT" "DELETE"} method))
      {::anom/category ::anom/unsupported
       ::anom/message (str "Unsupported method `" method "`.")
       :fhir/issue "not-supported"}

      (nil? type)
      {::anom/category ::anom/incorrect
       ::anom/message "Can't parse type from `entry.request.url` `" url "`."
       :fhir/issue "value"}

      (nil? (util/cached-entity db (keyword type)))
      {::anom/category ::anom/incorrect
       ::anom/message (str "Unknown type `" type "`.")
       :fhir/issue "value"}

      (and (#{"POST" "PUT"} method) (not (map? resource)))
      {::anom/category ::anom/incorrect
       :fhir/issue "structure"
       :fhir/operation-outcome "MSG_JSON_OBJECT"}

      (and (#{"POST" "PUT"} method) (not= type (get resource "resourceType")))
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"}

      (and (= "PUT" method) (nil? id))
      {::anom/category ::anom/incorrect
       ::anom/message "Can't parse id from `entry.request.url` `" url "`."
       :fhir/issue "value"}

      (and (= "PUT" method) (not= id (get resource "id")))
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"}

      :else
      (assoc entry :blaze/type type :blaze/id id))))


(defmulti validate-entries (fn [_ type _] type))


(defmethod validate-entries "batch"
  [db _ entries]
  (mapv
    (fn [entry]
      (let [validated-entry (validate-entry db entry)]
        (if (::anom/category validated-entry)
          (assoc entry :response (handler-util/bundle-error-response validated-entry))
          validated-entry)))
    entries))


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
       ::anom/message (str "Expected a Bundle but was `" resourceType "`.")
       :fhir/issue "value"})

    (not (#{"batch" "transaction"} type))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       ::anom/message
       (str "Expected a Bundle type of batch or transaction but was `" type "`.")
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
  (try
    (handler-fhir-util/upsert-resource conn db :server-assigned-id resource)
    (catch Exception e
      (md/error-deferred (ex-data e)))))


(defmethod upsert-entry "PUT"
  [conn db {:strs [resource]}]
  (try
    (handler-fhir-util/upsert-resource conn db :client-assigned-id resource)
    (catch Exception e
      (md/error-deferred (ex-data e)))))


(defmethod upsert-entry "DELETE"
  [conn db {:blaze/keys [type id]}]
  (try
    (handler-fhir-util/delete-resource conn db type id)
    (catch Exception e
      (md/error-deferred (ex-data e)))))


(defmulti transact
  {:arglists '([conn db type prepared-entries])}
  (fn [_ _ type _] type))


(defmethod transact "batch" [conn db _ entries]
  (md/loop [[entry & entries] entries
            results []]
    (if entry
      (if (:response entry)
        (md/recur entries (conj results entry))
        (-> (upsert-entry conn db entry)
            (md/chain'
              (fn [tx-result]
                (md/recur
                  entries
                  (conj
                    results
                    (assoc entry :blaze/tx-result tx-result)))))
            (md/catch'
              (fn [error]
                (md/recur
                  entries
                  (conj
                    results
                    (assoc entry
                      :response (handler-util/bundle-error-response error))))))))
      results)))


(defn- transact-resources [conn db entries]
  (-> (tx/transact-async conn (bundle/tx-data db entries))
      (md/chain'
        (fn [tx-result]
          (mapv #(assoc % :blaze/tx-result tx-result) entries)))))


(defmethod transact "transaction" [conn db _ entries]
  (let [code-tx-data (bundle/code-tx-data db entries)]
    (if (empty? code-tx-data)
      (transact-resources conn db entries)
      (-> (tx/transact-async conn code-tx-data)
          (md/chain'
            (fn [{db :db-after}]
              (transact-resources conn db entries)))))))


(defn- build-entry
  [base-uri
   {{type "resourceType" id "id"} "resource" {db :db-after} :blaze/tx-result
    :blaze/keys [old-resource] :keys [response] :as entry}]
  (if response
    (dissoc entry :blaze/type :blaze/id)
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
          (dissoc :blaze/type :blaze/id :blaze/tx-result)))))


(defn- build-response [base-uri bundle-type entries]
  (ring/response
    {:resourceType "Bundle"
     :type (str bundle-type "-response")
     :entry (mapv #(build-entry base-uri %) entries)}))


(defn- handler-intern [base-uri conn]
  (fn [{{:strs [type] :as bundle} :body}]
    (let [db (d/db conn)]
      (-> (validate-and-prepare-bundle db bundle)
          (md/chain' #(transact conn db type %))
          (md/chain' #(build-response base-uri type %))
          (md/catch' handler-util/error-response)))))


(defn wrap-interaction-name [handler]
  (fn [{{:strs [type]} :body :as request}]
    (-> (handler request)
        (md/chain'
          (fn [response]
            (assoc response :fhir/interaction-name type))))))


(s/def :handler.fhir/transaction fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/transaction)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-interaction-name)
      (wrap-observe-request-duration)))
