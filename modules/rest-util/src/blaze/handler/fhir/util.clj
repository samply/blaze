(ns blaze.handler.fhir.util
  "Utilities for FHIR interactions. Main functions are `upsert-resource` and
  `delete-resource`."
  (:require
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as datomic-util]
    [blaze.executors :refer [executor?]]
    [blaze.terminology-service :refer [term-service?]]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md :refer [deferrable?]]
    [reitit.core :as reitit]))


(defn- increment-total [type]
  [[:fn/increment-type-total (keyword type) 1]
   [:fn/increment-system-total 1]])


(defn- increment-version [type]
  [[:fn/increment-type-version (keyword type) 1]
   [:fn/increment-system-version 1]])


(defn- update-system-and-type-tx-data
  [db tempid {type "resourceType" id "id"}]
  (let [resource (datomic-util/resource db type id)]
    (cond->
      (increment-version type)

      (or (nil? resource) (datomic-util/deleted? resource))
      (into (increment-total type))

      (nil? resource)
      (conj [:db/add "datomic.tx" :tx/resources tempid])

      (some? resource)
      (conj [:db/add "datomic.tx" :tx/resources (:db/id resource)]))))


(defn- upsert-resource* [transaction-executor conn db creation-mode resource]
  (let [[type id tempid] (tx/resource-tempid db resource)
        tempids (when tempid {type {id tempid}})
        tx-data (tx/resource-upsert db tempids creation-mode resource)]
    (if (empty? tx-data)
      {:db-after db}
      (tx/transact-async
        transaction-executor
        conn
        (into tx-data (update-system-and-type-tx-data db tempid resource))))))


(s/fdef upsert-resource
  :args
  (s/cat
    :transaction-executor executor?
    :conn ::ds/conn
    :term-service term-service?
    :db ::ds/db
    :creation-mode ::tx/creation-mode
    :resource ::tx/resource)
  :ret deferrable?)

(defn upsert-resource
  "Upserts `resource` and returns the deferred transaction result."
  [transaction-executor conn term-service db creation-mode resource]
  (-> (tx/annotate-codes term-service db resource)
      (md/chain'
        (fn [resource]
          (-> (tx/resource-codes-creation db resource)
              (md/chain'
                (fn [tx-data]
                  (if (empty? tx-data)
                    (upsert-resource*
                      transaction-executor
                      conn
                      db
                      creation-mode
                      resource)
                    (-> (tx/transact-async transaction-executor conn tx-data)
                        (md/chain'
                          (fn [{db :db-after}]
                            (upsert-resource*
                              transaction-executor
                              conn
                              db
                              creation-mode
                              resource))))))))))))


(defn- decrement-total [type]
  [[:fn/increment-type-total (keyword type) -1]
   [:fn/increment-system-total -1]])


(defn- delete-system-and-type-tx-data [db type id]
  (let [resource (datomic-util/resource db type id)]
    (-> (decrement-total type)
        (into (increment-version type))
        (conj [:db/add "datomic.tx" :tx/resources (:db/id resource)]))))


(s/fdef delete-resource
  :args
  (s/cat
    :transaction-executor executor?
    :conn ::ds/conn
    :db ::ds/db
    :type string?
    :id string?))

(defn delete-resource
  [transaction-executor conn db type id]
  (-> (md/future (tx/resource-deletion db type id))
      (md/chain'
        (fn [tx-data]
          (if (empty? tx-data)
            {:db-after db}
            (tx/transact-async
              transaction-executor
              conn
              (into tx-data (delete-system-and-type-tx-data db type id))))))))


(s/fdef t
  :args (s/cat :query-params (s/map-of string? string?))
  :ret (s/nilable nat-int?))

(defn t
  "Returns the t (optional) of the database which should be stay stable."
  {:arglists '([query-params])}
  [{:strs [t]}]
  (when (some->> t (re-matches #"\d+"))
    (Long/parseLong t)))


(def ^:private ^:const default-page-size 50)
(def ^:private ^:const max-page-size 500)


(s/fdef page-size
  :args (s/cat :query-params (s/map-of string? string?))
  :ret nat-int?)

(defn page-size
  "Returns the page size taken from a possible `_count` query param.

  The default page size is 50 and the maximum page size is 500."
  {:arglists '([query-params])}
  [{count "_count"}]
  (if (some->> count (re-matches #"\d+"))
    (min (Long/parseLong count) max-page-size)
    default-page-size))


(s/fdef type-url
  :args (s/cat :router reitit/router? :type string?)
  :ret string?)

(defn type-url
  "Returns the URL of a resource type like `[base]/[type]`."
  [router type]
  (let [{:keys [path] {:blaze/keys [base-url]} :data}
        (reitit/match-by-name router (keyword type "type"))]
    (str base-url path)))


(s/fdef instance-url
  :args (s/cat :router reitit/router? :type string? :id string?)
  :ret string?)

(defn instance-url
  "Returns the URL of a instance (resource) like `[base]/[type]/[id]`."
  [router type id]
  (let [{:keys [path] {:blaze/keys [base-url]} :data}
        (reitit/match-by-name router (keyword type "instance") {:id id})]
    (str base-url path)))


(s/fdef versioned-instance-url
  :args (s/cat :router reitit/router? :type string? :id string? :vid string?)
  :ret string?)

(defn versioned-instance-url
  "Returns the URL of a versioned instance (resource) like
  `[base]/[type]/[id]/_history/[vid]`."
  [router type id vid]
  (let [{:keys [path] {:blaze/keys [base-url]} :data}
        (reitit/match-by-name
          router (keyword type "versioned-instance") {:id id :vid vid})]
    (str base-url path)))
