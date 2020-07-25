(ns blaze.db.api-stub
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.node :refer [new-node]]
    [blaze.db.resource-store.kv :refer [new-kv-resource-store]]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.tx-log-spec]
    [blaze.db.tx-log.local :refer [new-local-tx-log]]
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st])
  (:refer-clojure :exclude [sync])
  (:import
    [java.time Clock Instant ZoneId Duration]))


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

(defn mem-node []
  (let [index-kv-store
        (new-mem-kv-store
          {:search-param-value-index nil
           :resource-value-index nil
           :compartment-search-param-value-index nil
           :compartment-resource-type-index nil
           :active-search-params nil
           :tx-success-index nil
           :tx-error-index nil
           :t-by-instant-index nil
           :resource-as-of-index nil
           :type-as-of-index nil
           :system-as-of-index nil
           :type-stats-index nil
           :system-stats-index nil})
        resource-store (new-kv-resource-store (new-mem-kv-store))
        tx-log (new-local-tx-log (new-mem-kv-store) clock local-tx-log-executor)]
    (new-node tx-log resource-indexer-executor 1 indexer-executor
              index-kv-store resource-store search-param-registry
              (Duration/ofMillis 10))))


(defn- submit-txs [node txs]
  (doseq [tx-ops txs]
    @(d/transact node tx-ops)))


(defn mem-node-with [txs]
  (doto (mem-node)
    (submit-txs txs)))


(defn resource [db type id res-spec]
  (st/instrument
    [`d/resource]
    {:spec
     {`d/resource
      (s/fspec
        :args (s/cat :db #{db} :type #{type} :id #{id})
        :ret res-spec)}
     :stub
     #{`d/resource}}))
