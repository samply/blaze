(ns blaze.db.indexer.resource
  (:require
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.indexer :as indexer]
    [blaze.db.kv :as kv]
    [blaze.db.kv.spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.search-param-registry.spec]
    [blaze.executors :as ex]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.nippy :as nippy]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Durations in resource indexer."
  {:namespace "blaze"
   :subsystem "db"
   :name "resource_indexer_duration_seconds"}
  (mapcat #(list % (* 2.5 %) (* 5 %) (* 7.5 %)) (take 5 (iterate #(* 10 %) 0.00001)))
  "op")


(defn- compartment-resource-type-entry
  "Returns an entry into the :compartment-resource-type-index where `resource`
  is linked to `compartment`."
  {:arglists '([compartment resource])}
  [[comp-code comp-id] {type :resourceType id :id}]
  [:compartment-resource-type-index
   (codec/compartment-resource-type-key
     (codec/c-hash comp-code) (codec/id-bytes comp-id)
     (codec/tid type) (codec/id-bytes id))
   bytes/empty])


(defn- index-entries [linked-compartments search-param hash resource]
  (let [res (search-param/index-entries search-param hash resource linked-compartments)]
    (if (::anom/category res)
      (log/warn (format "Skip indexing for search parameter `%s` on resource `%s/%s`. Cause: %s" (:url search-param) (:resourceType resource) (:id resource) (::anom/message res)))
      res)))


(defn- calc-search-params [search-param-registry hash resource]
  (with-open [_ (prom/timer duration-seconds "calc-search-params")]
    (let [linked-compartments (sr/linked-compartments search-param-registry resource)]
      (into
        (into
          []
          (map #(compartment-resource-type-entry % resource))
          linked-compartments)
        (mapcat #(index-entries linked-compartments % hash resource))
        (sr/list-by-type search-param-registry (:resourceType resource))))))


(defn- freeze [resource]
  (with-open [_ (prom/timer duration-seconds "freeze")]
    (nippy/fast-freeze resource)))


(defn- calc-entries [search-param-registry hash resource]
  (-> (calc-search-params search-param-registry hash resource)
      (conj [:resource-index hash (freeze resource)])))


(defn- put [store entries]
  (with-open [_ (prom/timer duration-seconds "put")]
    (kv/put store entries)))


(deftype ResourceIndexer [search-param-registry kv-store executor]
  indexer/Resource
  (-index-resources [_ hash-and-resources]
    (md/future-with executor
      (->> (into
             []
             (mapcat
               (fn [[hash resource]]
                 (calc-entries search-param-registry hash resource)))
             hash-and-resources)
           (put kv-store)))))


(defn init-resource-indexer [search-param-registry store executor]
  (->ResourceIndexer search-param-registry store executor))


(s/def ::executor
  ex/executor?)


(defmethod ig/pre-init-spec ::indexer/resource-indexer [_]
  (s/keys
    :req-un
    [:blaze.db/search-param-registry
     :blaze.db/kv-store
     ::executor]))


(defmethod ig/init-key ::indexer/resource-indexer
  [_ {:keys [search-param-registry kv-store executor]}]
  (log/info "Init resource indexer")
  (init-resource-indexer search-param-registry kv-store executor))


(defn- executor-init-msg [num-threads]
  (format "Init resource indexer executor with %d threads" num-threads))


(defmethod ig/init-key ::executor
  [_ {:keys [num-threads] :or {num-threads 4}}]
  (log/info (executor-init-msg num-threads))
  (ex/io-pool num-threads "resource-indexer-%d"))


(derive ::executor :blaze.metrics/thread-pool-executor)


(reg-collector ::duration-seconds
  duration-seconds)
