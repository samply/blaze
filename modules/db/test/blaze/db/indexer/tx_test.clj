(ns blaze.db.indexer.tx-test
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.indexer.tx :as tx]
    [blaze.db.indexer.tx-spec]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [init-mem-kv-store]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.walk :refer [postwalk]]
    [cognitect.anomalies :as anom])
  (:import
    [com.github.benmanes.caffeine.cache LoadingCache]
    [java.time Instant]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn init-store []
  (init-mem-kv-store
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
     :type-stats-index nil
     :system-stats-index nil}))


(def empty-store (init-store))

(def resource-cache (reify LoadingCache))

(def now (Instant/now))

(def tid-patient (codec/tid "Patient"))

(def patient-0 {:resourceType "Patient" :id "0"})
(def patient-0-v2 {:resourceType "Patient" :id "0" :gender "male"})
(def patient-1 {:resourceType "Patient" :id "1"})
(def patient-2 {:resourceType "Patient" :id "2"})


(def store-patient-0
  (let [s (init-store)
        cmds [[:put "Patient" "0" (codec/hash patient-0)]]]
    (kv/put s (tx/verify-tx-cmds s 1 now cmds))
    s))


(defn bytes->vec [x]
  (if (bytes? x) (vec x) x))


(defmacro is-entries= [a b]
  `(is (= (postwalk bytes->vec ~a) (postwalk bytes->vec ~b))))


(deftest verify-tx-cmds
  (testing "adding one patient to an empty store"
    (is-entries=
      (tx/verify-tx-cmds
        empty-store 1 now
        [[:put "Patient" "0" (codec/hash patient-0)]])
      (into
        (codec/tx-success-entries 1 now)
        (let [value (codec/resource-as-of-value (codec/hash patient-0) (codec/state 1 :put))]
          [[:resource-as-of-index
            (codec/resource-as-of-key tid-patient (codec/id-bytes "0") 1)
            value]
           [:type-as-of-index
            (codec/type-as-of-key tid-patient 1 (codec/id-bytes "0"))
            value]
           [:system-as-of-index
            (codec/system-as-of-key 1 tid-patient (codec/id-bytes "0"))
            value]
           (type-stats/entry tid-patient 1 {:total 1 :num-changes 1})
           (system-stats/entry 1 {:total 1 :num-changes 1})]))))

  (testing "adding a second version of a patient to a store containing it already"
    (is-entries=
      (tx/verify-tx-cmds
        store-patient-0 2 now
        [[:put "Patient" "0" (codec/hash patient-0-v2)]])
      (into
        (codec/tx-success-entries 2 now)
        (let [value (codec/resource-as-of-value (codec/hash patient-0-v2) (codec/state 2 :put))]
          [[:resource-as-of-index
            (codec/resource-as-of-key tid-patient (codec/id-bytes "0") 2)
            value]
           [:type-as-of-index
            (codec/type-as-of-key tid-patient 2 (codec/id-bytes "0"))
            value]
           [:system-as-of-index
            (codec/system-as-of-key 2 tid-patient (codec/id-bytes "0"))
            value]
           (type-stats/entry tid-patient 2 {:total 1 :num-changes 2})
           (system-stats/entry 2 {:total 1 :num-changes 2})]))))

  (testing "adding a second version of a patient to a store containing it already incl. matcher"
    (is-entries=
      (tx/verify-tx-cmds
        store-patient-0 2 now
        [[:put "Patient" "0" (codec/hash patient-0-v2) 1]])
      (into
        (codec/tx-success-entries 2 now)
        (let [value (codec/resource-as-of-value (codec/hash patient-0-v2) (codec/state 2 :put))]
          [[:resource-as-of-index
            (codec/resource-as-of-key tid-patient (codec/id-bytes "0") 2)
            value]
           [:type-as-of-index
            (codec/type-as-of-key tid-patient 2 (codec/id-bytes "0"))
            value]
           [:system-as-of-index
            (codec/system-as-of-key 2 tid-patient (codec/id-bytes "0"))
            value]
           (type-stats/entry tid-patient 2 {:total 1 :num-changes 2})
           (system-stats/entry 2 {:total 1 :num-changes 2})]))))

  (testing "deleting the existing patient"
    (is-entries=
      (tx/verify-tx-cmds
        store-patient-0 2 now
        [[:delete "Patient" "0" (codec/hash (codec/deleted-resource "Patient" "0"))]])
      (into
        (codec/tx-success-entries 2 now)
        (let [value (codec/resource-as-of-value (codec/hash (codec/deleted-resource "Patient" "0"))
                                                (codec/state 2 :delete))]
          [[:resource-as-of-index
            (codec/resource-as-of-key tid-patient (codec/id-bytes "0") 2)
            value]
           [:type-as-of-index
            (codec/type-as-of-key tid-patient 2 (codec/id-bytes "0"))
            value]
           [:system-as-of-index
            (codec/system-as-of-key 2 tid-patient (codec/id-bytes "0"))
            value]
           (type-stats/entry tid-patient 2 {:total 0 :num-changes 2})
           (system-stats/entry 2 {:total 0 :num-changes 2})]))))

  (testing "adding a second patient to a store containing already one"
    (is-entries=
      (tx/verify-tx-cmds
        store-patient-0 2 now
        [[:put "Patient" "1" (codec/hash patient-1)]])
      (into
        (codec/tx-success-entries 2 now)
        (let [value (codec/resource-as-of-value (codec/hash patient-1) (codec/state 1 :put))]
          [[:resource-as-of-index
            (codec/resource-as-of-key tid-patient (codec/id-bytes "1") 2)
            value]
           [:type-as-of-index
            (codec/type-as-of-key tid-patient 2 (codec/id-bytes "1"))
            value]
           [:system-as-of-index
            (codec/system-as-of-key 2 tid-patient (codec/id-bytes "1"))
            value]
           (type-stats/entry tid-patient 2 {:total 2 :num-changes 2})
           (system-stats/entry 2 {:total 2 :num-changes 2})]))))

  (testing "update conflict"
    (is-entries=
      (tx/verify-tx-cmds
        store-patient-0 2 now
        [[:put "Patient" "0" (codec/hash patient-1) 0]])
      (codec/tx-error-entries
        2
        {::anom/category ::anom/conflict
         ::anom/message (format "put mismatch for %s/%s" "Patient" "0")}))))
