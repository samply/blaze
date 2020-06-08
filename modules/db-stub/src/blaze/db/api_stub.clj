(ns blaze.db.api-stub
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.kv.mem :refer [init-mem-kv-store]]
    [blaze.db.indexer.resource :refer [init-resource-indexer]]
    [blaze.db.indexer.tx :refer [init-tx-indexer]]
    [blaze.db.node :as node :refer [new-node]]
    [blaze.db.node-spec]
    [blaze.db.resource-cache :refer [new-resource-cache]]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.tx-log.local :refer [init-local-tx-log]]
    [blaze.db.tx-log-spec]
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st])
  (:refer-clojure :exclude [sync])
  (:import
    [java.time Clock Instant ZoneId]))


(def search-param-registry (sr/init-search-param-registry))


(defn mem-node []
  (let [kv-store
        (init-mem-kv-store
          {:search-param-value-index nil
           :resource-value-index nil
           :compartment-search-param-value-index nil
           :compartment-resource-value-index nil
           :compartment-resource-type-index nil
           :resource-index nil
           :active-search-params nil
           :tx-success-index nil
           :tx-error-index nil
           :t-by-instant-index nil
           :resource-as-of-index nil
           :type-as-of-index nil
           :system-as-of-index nil
           :type-stats-index nil
           :system-stats-index nil})
        ri (init-resource-indexer search-param-registry kv-store
                                  (ex/cpu-bound-pool "resource-indexer-%d"))
        ti (init-tx-indexer kv-store)
        clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC"))
        tx-log (init-local-tx-log ri 1 ti clock)
        resource-cache (new-resource-cache kv-store 0)]
    (new-node tx-log ti kv-store resource-cache search-param-registry)))


(defn- submit-txs [node txs]
  (doseq [tx-ops txs]
    @(d/submit-tx node tx-ops)))


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
