(ns blaze.db.node-test
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.impl.db-spec]
    [blaze.db.impl.protocols :as p]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.kv.mem-spec]
    [blaze.db.node :as node]
    [blaze.db.node-spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.kv :refer [new-kv-resource-store]]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.tx-log-spec]
    [blaze.db.tx-log.local :refer [new-local-tx-log]]
    [blaze.db.tx-log.local-spec]
    [blaze.executors :as ex]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [java-time :as jt]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine]
    [java.time Clock Instant ZoneId]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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
     :tx-success-index {:reverse-comparator? true}
     :tx-error-index nil
     :t-by-instant-index {:reverse-comparator? true}
     :resource-as-of-index nil
     :type-as-of-index nil
     :system-as-of-index nil
     :type-stats-index nil
     :system-stats-index nil}))


(def clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))


(defn new-node-with [{:keys [resource-store]}]
  (let [tx-log (new-local-tx-log (new-mem-kv-store) clock local-tx-log-executor)
        resource-handle-cache (.build (Caffeine/newBuilder))]
    (node/new-node tx-log resource-handle-cache resource-indexer-executor 1
                   indexer-executor (new-index-kv-store) resource-store
                   search-param-registry (jt/millis 10))))


(defn new-node []
  (new-node-with
    {:resource-store (new-kv-resource-store (new-mem-kv-store))}))


(defn new-resource-store-failing-on-get []
  (reify
    rs/ResourceLookup
    (-get [_ _]
      (ac/failed-future (ex-anom {::anom/category ::anom/fault})))
    (-multi-get [_ _]
      (ac/failed-future (ex-anom {::anom/category ::anom/fault})))
    rs/ResourceStore
    (-put [_ _]
      (ac/completed-future nil))))


(deftest transact-test
  (testing "with slow transaction result fetching"
    (testing "create"
      (testing "one Patient"
        (with-open [node (new-node)]
          @(-> (p/-submit-tx node [[:create {:fhir/type :fhir/Patient :id "0"}]])
               (ac/then-compose
                 (fn [t]
                   (Thread/sleep 100)
                   (p/-tx-result node t))))

          (given @(d/pull node (d/resource-handle (d/db node) "Patient" "0"))
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [meta :blaze.db/op] := :create))))

    (testing "with failing resource storage"
      (testing "on get"
        (with-open [node (new-node-with
                           {:resource-store (new-resource-store-failing-on-get)})]

          (try
            @(-> (p/-submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"}]])
                 (ac/then-compose
                   (fn [t]
                     (Thread/sleep 100)
                     (p/-tx-result node t))))
            (catch Exception e
              (given (ex-data (ex-cause e))
                ::anom/category := ::anom/fault))))))))
