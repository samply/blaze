(ns blaze.db.node.tx-indexer.verify-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.db.impl.index.rts-as-of :as rts]
    [blaze.db.impl.index.system-as-of :as sao]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.type-as-of :as tao]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.kv.mem-spec]
    [blaze.db.node :as node]
    [blaze.db.node.tx-indexer.verify :as verify]
    [blaze.db.node.tx-indexer.verify-spec]
    [blaze.db.resource-store.kv :refer [new-kv-resource-store]]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.tx-log.local :refer [new-local-tx-log]]
    [blaze.executors :as ex]
    [blaze.fhir.hash :as hash]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.walk :refer [postwalk]]
    [cognitect.anomalies :as anom]
    [java-time :as jt]
    [juxt.iota :refer [given]])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine]
    [java.time Clock Instant ZoneId]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
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


(defn new-node []
  (let [tx-log (new-local-tx-log (new-mem-kv-store) clock local-tx-log-executor)
        resource-handle-cache (.build (Caffeine/newBuilder))]
    (node/new-node tx-log resource-handle-cache resource-indexer-executor 1
                   indexer-executor (new-index-kv-store)
                   (new-kv-resource-store (new-mem-kv-store))
                   search-param-registry (jt/millis 10))))


(def tid-patient (codec/tid "Patient"))

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-0-v2 {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})
(def patient-2 {:fhir/type :fhir/Patient :id "2"})


(defn bytes->vec [x]
  (if (bytes? x) (vec x) x))


(defmacro is-entries= [a b]
  `(is (= (postwalk bytes->vec ~a) (postwalk bytes->vec ~b))))


(deftest verify-tx-cmds
  (testing "adding one patient to an empty store"
    (with-open [node (new-node)]
      (is-entries=
        (verify/verify-tx-cmds
          (d/db node) 1
          [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0)}])
        (let [value (rts/encode-value (hash/generate patient-0) 1 :put)]
          [[:resource-as-of-index
            (rao/encode-key tid-patient (codec/id-byte-string "0") 1)
            value]
           [:type-as-of-index
            (tao/encode-key tid-patient 1 (codec/id-byte-string "0"))
            value]
           [:system-as-of-index
            (sao/encode-key 1 tid-patient (codec/id-byte-string "0"))
            value]
           (type-stats/index-entry tid-patient 1 {:total 1 :num-changes 1})
           (system-stats/index-entry 1 {:total 1 :num-changes 1})]))))

  (testing "adding a second version of a patient to a store containing it already"
    (with-open [node (new-node)]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])
      (is-entries=
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0-v2)}])
        (let [value (rts/encode-value (hash/generate patient-0-v2) 2 :put)]
          [[:resource-as-of-index
            (rao/encode-key tid-patient (codec/id-byte-string "0") 2)
            value]
           [:type-as-of-index
            (tao/encode-key tid-patient 2 (codec/id-byte-string "0"))
            value]
           [:system-as-of-index
            (sao/encode-key 2 tid-patient (codec/id-byte-string "0"))
            value]
           (type-stats/index-entry tid-patient 2 {:total 1 :num-changes 2})
           (system-stats/index-entry 2 {:total 1 :num-changes 2})]))))

  (testing "adding a second version of a patient to a store containing it already incl. matcher"
    (with-open [node (new-node)]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])
      (is-entries=
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0-v2) :if-match 1}])
        (let [value (rts/encode-value (hash/generate patient-0-v2) 2 :put)]
          [[:resource-as-of-index
            (rao/encode-key tid-patient (codec/id-byte-string "0") 2)
            value]
           [:type-as-of-index
            (tao/encode-key tid-patient 2 (codec/id-byte-string "0"))
            value]
           [:system-as-of-index
            (sao/encode-key 2 tid-patient (codec/id-byte-string "0"))
            value]
           (type-stats/index-entry tid-patient 2 {:total 1 :num-changes 2})
           (system-stats/index-entry 2 {:total 1 :num-changes 2})]))))

  (testing "deleting the existing patient"
    (with-open [node (new-node)]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])
      (is-entries=
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "delete" :type "Patient" :id "0"
            :hash (hash/generate (codec/deleted-resource "Patient" "0"))}])
        (let [value (rts/encode-value (hash/generate (codec/deleted-resource "Patient" "0"))
                                      2 :delete)]
          [[:resource-as-of-index
            (rao/encode-key tid-patient (codec/id-byte-string "0") 2)
            value]
           [:type-as-of-index
            (tao/encode-key tid-patient 2 (codec/id-byte-string "0"))
            value]
           [:system-as-of-index
            (sao/encode-key 2 tid-patient (codec/id-byte-string "0"))
            value]
           (type-stats/index-entry tid-patient 2 {:total 0 :num-changes 2})
           (system-stats/index-entry 2 {:total 0 :num-changes 2})]))))

  (testing "adding a second patient to a store containing already one"
    (with-open [node (new-node)]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])
      (is-entries=
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "put" :type "Patient" :id "1" :hash (hash/generate patient-1)}])
        (let [value (rts/encode-value (hash/generate patient-1) 1 :put)]
          [[:resource-as-of-index
            (rao/encode-key tid-patient (codec/id-byte-string "1") 2)
            value]
           [:type-as-of-index
            (tao/encode-key tid-patient 2 (codec/id-byte-string "1"))
            value]
           [:system-as-of-index
            (sao/encode-key 2 tid-patient (codec/id-byte-string "1"))
            value]
           (type-stats/index-entry tid-patient 2 {:total 2 :num-changes 2})
           (system-stats/index-entry 2 {:total 2 :num-changes 2})]))))

  (testing "update conflict"
    (with-open [node (new-node)]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])
      (try
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0) :if-match 0}])
        (catch Exception e
          (given e
            ::anom/category := ::anom/conflict
            ::anom/message := (format "Precondition `W/\"0\"` failed on `Patient/0`.")
            :http/status := 412))))))
