(ns blaze.db.node-dev
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [init-mem-kv-store]]
    [blaze.db.impl.codec :as codec]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.indexer.resource :refer [init-resource-indexer]]
    [blaze.db.indexer.tx :refer [init-tx-indexer]]
    [blaze.db.node :as node :refer [init-node]]
    [blaze.db.node-spec]
    [blaze.db.tx-log.local :refer [init-local-tx-log]]
    [blaze.db.tx-log.local-spec]
    [blaze.db.tx-log-spec]
    [blaze.executors :as ex]
    [clojure.spec.test.alpha :as st])
  (:import
    [java.time Clock Instant ZoneId]))


(st/instrument)


(def kv-store
  (init-mem-kv-store
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-value-index nil
     :resource-type-index nil
     :compartment-resource-type-index nil
     :resource-index nil
     :active-search-params nil
     :tx-success-index nil
     :tx-error-index nil
     :t-by-instant-index nil
     :resource-as-of-index nil
     :type-as-of-index nil
     :type-stats-index nil
     :system-stats-index nil}))

(def search-param-registry (sr/init-mem-search-param-registry))

(def r-i (init-resource-indexer search-param-registry kv-store
                                (ex/cpu-bound-pool "resource-indexer-%d")))

(def tx-i (init-tx-indexer kv-store))

(def clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))

(def tx-log (init-local-tx-log r-i 1 tx-i clock))

(def resource-cache (node/resource-cache kv-store 0))

(def node (init-node tx-log tx-i kv-store resource-cache search-param-registry))


(comment
  (def db (d/db node))
  (def db @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :a 1}]]))
  (def db @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :a 2}]]))
  (def db @(d/submit-tx node [[:put {:resourceType "Patient" :id "1" :a 1}]]))
  (def db @(d/submit-tx node [[:delete "Patient" "0"]]))

  (d/resource db "Patient" "0")
  (d/resource db "Patient" "1")
  (into [] (d/list-resources db "Patient"))

  (d/total-num-of-instance-changes db "Patient" "0" nil)
  (into [] (d/instance-history db "Patient" "0" nil nil))

  (d/total-num-of-instance-changes db "Patient" "0" {:since (.minusSeconds (Instant/now) 20)})
  (into [] (d/instance-history db "Patient" "0" {:since (.minusSeconds (Instant/now) 20)}))

  (d/total-num-of-type-changes db "Patient")
  (into [] (d/type-history db "Patient" nil nil nil))

  (def i (kv/new-iterator (kv/new-snapshot kv-store) :type-as-of-index))
  (codec/type-as-of-key->t (kv/seek-to-first i))
  (codec/type-as-of-key->t (kv/next i))

  (def i (kv/new-iterator (kv/new-snapshot kv-store) :type-stats-index))
  (vec (kv/seek-to-first i))

  (clojure.repl/pst)
  )
