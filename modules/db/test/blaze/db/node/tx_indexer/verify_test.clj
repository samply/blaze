(ns blaze.db.node.tx-indexer.verify-test
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.node.tx-indexer.verify :as verify]
    [blaze.db.node.tx-indexer.verify-spec]
    [blaze.fhir.hash :as hash]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.walk :refer [postwalk]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn init-store []
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
     :type-stats-index nil
     :system-stats-index nil}))


(def empty-store (init-store))

(def tid-patient (codec/tid "Patient"))

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-0-v2 {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})
(def patient-2 {:fhir/type :fhir/Patient :id "2"})


(def store-patient-0
  (let [kv-store (init-store)
        cmds [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0)}]]
    (kv/put kv-store (verify/verify-tx-cmds kv-store 1 cmds))
    kv-store))


(defn bytes->vec [x]
  (if (bytes? x) (vec x) x))


(defmacro is-entries= [a b]
  `(is (= (postwalk bytes->vec ~a) (postwalk bytes->vec ~b))))


(deftest verify-tx-cmds
  (testing "adding one patient to an empty store"
    (is-entries=
      (verify/verify-tx-cmds
        empty-store 1
        [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0)}])
      (let [value (codec/resource-as-of-value (hash/generate patient-0) (codec/state 1 :put))]
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
         (system-stats/entry 1 {:total 1 :num-changes 1})])))

  (testing "adding a second version of a patient to a store containing it already"
    (is-entries=
      (verify/verify-tx-cmds
        store-patient-0 2
        [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0-v2)}])
      (let [value (codec/resource-as-of-value (hash/generate patient-0-v2) (codec/state 2 :put))]
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
         (system-stats/entry 2 {:total 1 :num-changes 2})])))

  (testing "adding a second version of a patient to a store containing it already incl. matcher"
    (is-entries=
      (verify/verify-tx-cmds
        store-patient-0 2
        [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0-v2) :if-match 1}])
      (let [value (codec/resource-as-of-value (hash/generate patient-0-v2) (codec/state 2 :put))]
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
         (system-stats/entry 2 {:total 1 :num-changes 2})])))

  (testing "deleting the existing patient"
    (is-entries=
      (verify/verify-tx-cmds
        store-patient-0 2
        [{:op "delete" :type "Patient" :id "0"
          :hash (hash/generate (codec/deleted-resource "Patient" "0"))}])
      (let [value (codec/resource-as-of-value (hash/generate (codec/deleted-resource "Patient" "0"))
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
         (system-stats/entry 2 {:total 0 :num-changes 2})])))

  (testing "adding a second patient to a store containing already one"
    (is-entries=
      (verify/verify-tx-cmds
        store-patient-0 2
        [{:op "put" :type "Patient" :id "1" :hash (hash/generate patient-1)}])
      (let [value (codec/resource-as-of-value (hash/generate patient-1) (codec/state 1 :put))]
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
         (system-stats/entry 2 {:total 2 :num-changes 2})])))

  (testing "update conflict"
    (try
      (verify/verify-tx-cmds
        store-patient-0 2
        [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0) :if-match 0}])
      (catch Exception e
        (given e
          ::anom/category := ::anom/conflict
          ::anom/message := (format "Precondition `W/\"0\"` failed on `Patient/0`.")
          :http/status := 412)))))
