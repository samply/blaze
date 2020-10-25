(ns blaze.db.api-test-perf
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.db.api :as d]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.node :as node]
    [blaze.db.resource-store.kv :refer [new-kv-resource-store]]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.tx-log.local :refer [new-local-tx-log]]
    [blaze.executors :as ex]
    [clojure.test :refer [deftest is testing]]
    [criterium.core :as criterium]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock Duration Instant ZoneId]))


(log/set-level! :info)


(def ^:private search-param-registry (sr/init-search-param-registry))


(def ^:private resource-indexer-executor
  (ex/cpu-bound-pool "resource-indexer-%d"))


;; TODO: with this shared executor, it's not possible to run test in parallel
(def ^:private local-tx-log-executor
  (ex/single-thread-executor "local-tx-log"))


;; TODO: with this shared executor, it's not possible to run test in parallel
(def ^:private indexer-executor
  (ex/single-thread-executor "indexer"))


(defn new-index-kv-store []
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
     :system-stats-index nil}))


(def clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))


(defn new-node []
  (let [tx-log (new-local-tx-log (new-mem-kv-store) clock local-tx-log-executor)]
    (node/new-node tx-log resource-indexer-executor 1 indexer-executor
                   (new-index-kv-store)
                   (new-kv-resource-store (new-mem-kv-store))
                   search-param-registry (Duration/ofMillis 10))))


(deftest transact-test
  (with-open [node (new-node)]
    ;; 190 µs
    (criterium/quick-bench
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]]))))
