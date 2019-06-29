(ns blaze.handler.fhir.util
  "Utilities for FHIR interactions. Main functions are `upsert-resource` and
  `delete-resource`."
  (:require
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]))


(defn- increment-total [type]
  [[:fn/increment-type-total (keyword type) 1]
   [:fn/increment-system-total 1]])


(defn- increment-version [type]
  [[:fn/increment-type-version (keyword type) 1]
   [:fn/increment-system-version 1]])


(defn- update-system-and-type-tx-data
  [db tempid {type "resourceType" id "id"}]
  (let [resource (util/resource db type id)]
    (cond->
      (increment-version type)

      (or (nil? resource) (util/deleted? resource))
      (into (increment-total type))

      (nil? resource)
      (conj [:db/add "datomic.tx" :tx/resources tempid])

      (some? resource)
      (conj [:db/add "datomic.tx" :tx/resources (:db/id resource)]))))


(defn- upsert-resource* [conn db creation-mode resource]
  (let [[type id tempid] (tx/resource-tempid db resource)
        tempids (when tempid {type {id tempid}})
        tx-data (tx/resource-upsert db tempids creation-mode resource)]
    (if (empty? tx-data)
      {:db-after db}
      (tx/transact-async
        conn (into tx-data (update-system-and-type-tx-data db tempid resource))))))


(s/fdef upsert-resource
  :args (s/cat :conn ::ds/conn :db ::ds/db
               :creation-mode ::tx/creation-mode
               :resource ::tx/resource))

(defn upsert-resource
  "Throws exceptions with `ex-data` containing an anomaly on errors or
  unsupported features."
  [conn db creation-mode resource]
  (let [tx-data (tx/resource-codes-creation db resource)]
    (if (empty? tx-data)
      (upsert-resource* conn db creation-mode resource)
      (-> (tx/transact-async conn tx-data)
          (md/chain'
            (fn [{db :db-after}]
              (upsert-resource* conn db creation-mode resource)))))))


(defn- decrement-total [type]
  [[:fn/increment-type-total (keyword type) -1]
   [:fn/increment-system-total -1]])


(defn- delete-system-and-type-tx-data [db type id]
  (let [resource (util/resource db type id)]
    (-> (decrement-total type)
        (into (increment-version type))
        (conj [:db/add "datomic.tx" :tx/resources (:db/id resource)]))))


(s/fdef delete-resource
  :args (s/cat :conn ::ds/conn :db ::ds/db :type string? :id string?))

(defn delete-resource
  "Throws exceptions with `ex-data` containing an anomaly on errors or
  unsupported features."
  [conn db type id]
  (let [tx-data (tx/resource-deletion db type id)]
    (if (empty? tx-data)
      {:db-after db}
      (tx/transact-async
        conn (into tx-data (delete-system-and-type-tx-data db type id))))))


(def ^:private ^:const max-page-size 50)


(defn page-size
  "Returns the page size as minimum from a possible `_count` param and the max
  page size of 50."
  {:arglists '([params])}
  [{count "_count"}]
  (if (and count (re-matches #"\d+" count))
    (min (Long/parseLong count) max-page-size)
    max-page-size))
