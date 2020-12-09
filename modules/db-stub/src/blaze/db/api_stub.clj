(ns blaze.db.api-stub
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.kv.mem-spec]
    [blaze.db.node :refer [new-node]]
    [blaze.db.resource-store.kv :refer [new-kv-resource-store]]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.spec]
    [blaze.db.tx-log-spec]
    [blaze.db.tx-log.local :refer [new-local-tx-log]]
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]
    [java-time :as jt])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine]
    [java.io Closeable]
    [java.time Clock Instant ZoneId]))


(def ^:private search-param-registry (sr/init-search-param-registry))


(def ^:private resource-indexer-executor
  (ex/cpu-bound-pool "resource-indexer-%d"))


;; TODO: with this shared executor, it's not possible to run test in parallel
(def ^:private local-tx-log-executor
  (ex/single-thread-executor "local-tx-log"))


;; TODO: with this shared executor, it's not possible to run test in parallel
(def ^:private indexer-executor
  (ex/single-thread-executor "indexer"))


(def ^:private clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))

(defn mem-node ^Closeable []
  (let [index-kv-store
        (new-mem-kv-store
          {:search-param-value-index nil
           :resource-value-index nil
           :compartment-search-param-value-index nil
           :compartment-resource-type-index nil
           :active-search-params nil
           :tx-success-index {:reverse-comparator? true}
           :tx-error-index nil
           :t-by-instant-index {:reverse-comparator? true}
           :resource-as-of-index nil
           :type-as-of-index nil
           :system-as-of-index nil
           :type-stats-index nil
           :system-stats-index nil})
        resource-store (new-kv-resource-store (new-mem-kv-store))
        tx-log (new-local-tx-log (new-mem-kv-store) clock local-tx-log-executor)
        resource-handle-cache (.build (Caffeine/newBuilder))]
    (new-node tx-log resource-handle-cache resource-indexer-executor 1
              indexer-executor index-kv-store resource-store
              search-param-registry (jt/millis 10))))


(defn- submit-txs [node txs]
  (doseq [tx-ops txs]
    @(d/transact node tx-ops)))


(defn mem-node-with ^Closeable [txs]
  (doto (mem-node)
    (submit-txs txs)))


(s/fdef mem-node-with
  :args (s/cat :txs (s/coll-of :blaze.db/tx-ops))
  :ret :blaze.db/node)
