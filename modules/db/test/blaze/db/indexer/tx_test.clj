(ns blaze.db.indexer.tx-test
  (:require
    [blaze.db.indexer.tx :as tx]
    [blaze.db.indexer.tx-spec]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [init-mem-kv-store]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.walk :refer [postwalk]])
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
     :compartment-resource-value-index nil
     :resource-type-index nil
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
        (index/tx-success-entries 1 now)
        [[:resource-as-of-index
          (codec/resource-as-of-key tid-patient (codec/id-bytes "0") 1)
          (codec/resource-as-of-value (codec/hash patient-0) (codec/state 1 :put))]
         [:type-as-of-index
          (codec/type-as-of-key tid-patient 1 (codec/id-bytes "0"))
          codec/empty-byte-array]
         [:system-as-of-index
          (codec/system-as-of-key 1 tid-patient (codec/id-bytes "0"))
          codec/empty-byte-array]
         [:type-stats-index
          (codec/type-stats-key tid-patient 1)
          (codec/type-stats-value 1 1)]
         [:system-stats-index
          (codec/system-stats-key 1)
          (codec/system-stats-value 1 1)]])))

  (testing "adding a second version of a patient to a store containing it already"
    (is-entries=
      (tx/verify-tx-cmds
        store-patient-0 2 now
        [[:put "Patient" "0" (codec/hash patient-0)]])
      (into
        (index/tx-success-entries 2 now)
        [[:resource-as-of-index
          (codec/resource-as-of-key tid-patient (codec/id-bytes "0") 2)
          (codec/resource-as-of-value (codec/hash patient-0) (codec/state 2 :put))]
         [:type-as-of-index
          (codec/type-as-of-key tid-patient 2 (codec/id-bytes "0"))
          codec/empty-byte-array]
         [:system-as-of-index
          (codec/system-as-of-key 2 tid-patient (codec/id-bytes "0"))
          codec/empty-byte-array]
         [:type-stats-index
          (codec/type-stats-key tid-patient 2)
          (codec/type-stats-value 1 2)]
         [:system-stats-index
          (codec/system-stats-key 2)
          (codec/system-stats-value 1 2)]])))

  (testing "deleting the existing patient"
    (is-entries=
      (tx/verify-tx-cmds
        store-patient-0 2 now
        [[:delete "Patient" "0" (codec/hash (codec/deleted-resource "Patient" "0"))]])
      (into
        (index/tx-success-entries 2 now)
        [[:resource-as-of-index
          (codec/resource-as-of-key tid-patient (codec/id-bytes "0") 2)
          (codec/resource-as-of-value (codec/hash (codec/deleted-resource "Patient" "0"))
                                      (codec/state 2 :delete))]
         [:type-as-of-index
          (codec/type-as-of-key tid-patient 2 (codec/id-bytes "0"))
          codec/empty-byte-array]
         [:system-as-of-index
          (codec/system-as-of-key 2 tid-patient (codec/id-bytes "0"))
          codec/empty-byte-array]
         [:type-stats-index
          (codec/type-stats-key tid-patient 2)
          (codec/type-stats-value 0 2)]
         [:system-stats-index
          (codec/system-stats-key 2)
          (codec/system-stats-value 0 2)]])))

  (testing "adding a second patient to a store containing already one"
    (is-entries=
      (tx/verify-tx-cmds
        store-patient-0 2 now
        [[:put "Patient" "1" (codec/hash patient-1)]])
      (into
        (index/tx-success-entries 2 now)
        [[:resource-as-of-index
          (codec/resource-as-of-key tid-patient (codec/id-bytes "1") 2)
          (codec/resource-as-of-value (codec/hash patient-1) (codec/state 1 :put))]
         [:type-as-of-index
          (codec/type-as-of-key tid-patient 2 (codec/id-bytes "1"))
          codec/empty-byte-array]
         [:system-as-of-index
          (codec/system-as-of-key 2 tid-patient (codec/id-bytes "1"))
          codec/empty-byte-array]
         [:type-stats-index
          (codec/type-stats-key tid-patient 2)
          (codec/type-stats-value 2 2)]
         [:system-stats-index
          (codec/system-stats-key 2)
          (codec/system-stats-value 2 2)]]))))


(comment
  (require '[criterium.core :refer [bench quick-bench]])
  (st/unstrument)

  (let [cmds [[:put "Patient" "0" (codec/hash patient-0)]
              [:put "Patient" "1" (codec/hash patient-1)]
              [:put "Patient" "2" (codec/hash patient-2)]]]
    (bench (tx/verify-tx-cmds empty-store 1 now cmds)))

  (clojure.repl/pst)
  )
