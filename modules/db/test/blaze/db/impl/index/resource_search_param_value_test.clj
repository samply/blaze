(ns blaze.db.impl.index.resource-search-param-value-test
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle-test-util :refer [resource-handle]]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.resource-search-param-value-spec]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.index.single-version-id-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {::kv/mem {:column-families {:resource-value-index nil}}})

(def ^:private hash
  #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F")

(def ^:private c-hash
  (codec/c-hash "subject"))

(def ^:private tid
  (codec/tid "Observation"))

(defn- patient-ref [id]
  (codec/tid-id (codec/tid "Patient") id))

(defn- with-snapshot
  "Calls `f` with a snapshot of a store containing the reference of
  Observation/0 to Patient/xa."
  [f]
  (with-system [{kv-store ::kv/mem} config]
    (kv/put! kv-store [(r-sp-v/index-entry tid "0" hash c-hash (patient-ref "xa"))])
    (with-open [snapshot (kv/new-snapshot kv-store)]
      (f snapshot))))

(def ^:private observation
  (resource-handle "Observation" "0" 1 hash))

(def ^:private observation-svi
  (svi/single-version-id "0" hash))

(deftest value-prefix-filter-test
  (with-snapshot
    (fn [snapshot]
      (testing "matches a prefix of the value"
        (given (into [] (r-sp-v/value-prefix-filter snapshot c-hash [(patient-ref "x")])
                     [observation])
          count := 1))

      (testing "matches the value"
        (given (into [] (r-sp-v/value-prefix-filter snapshot c-hash [(patient-ref "xa")])
                     [observation])
          count := 1))

      (testing "doesn't match a longer value"
        (given (into [] (r-sp-v/value-prefix-filter snapshot c-hash [(patient-ref "xab")])
                     [observation])
          count := 0)))))

(deftest single-version-id-value-prefix-filter-test
  (with-snapshot
    (fn [snapshot]
      (testing "matches a prefix of the value"
        (given (into [] (r-sp-v/single-version-id-value-prefix-filter
                         snapshot tid c-hash [(patient-ref "x")])
                     [observation-svi])
          count := 1))

      (testing "matches the value"
        (given (into [] (r-sp-v/single-version-id-value-prefix-filter
                         snapshot tid c-hash [(patient-ref "xa")])
                     [observation-svi])
          count := 1))

      (testing "doesn't match a longer value"
        (given (into [] (r-sp-v/single-version-id-value-prefix-filter
                         snapshot tid c-hash [(patient-ref "xab")])
                     [observation-svi])
          count := 0)))))

(deftest value-equals-filter-test
  (with-snapshot
    (fn [snapshot]
      (testing "doesn't match a prefix of the value"
        (given (into [] (r-sp-v/value-equals-filter snapshot c-hash [(patient-ref "x")])
                     [observation])
          count := 0))

      (testing "matches the value"
        (given (into [] (r-sp-v/value-equals-filter snapshot c-hash [(patient-ref "xa")])
                     [observation])
          count := 1))

      (testing "matches if one of the values matches"
        (given (into [] (r-sp-v/value-equals-filter
                         snapshot c-hash [(patient-ref "x") (patient-ref "xa")])
                     [observation])
          count := 1))

      (testing "doesn't match a longer value"
        (given (into [] (r-sp-v/value-equals-filter snapshot c-hash [(patient-ref "xab")])
                     [observation])
          count := 0)))))

(deftest single-version-id-value-equals-filter-test
  (with-snapshot
    (fn [snapshot]
      (testing "doesn't match a prefix of the value"
        (given (into [] (r-sp-v/single-version-id-value-equals-filter
                         snapshot tid c-hash [(patient-ref "x")])
                     [observation-svi])
          count := 0))

      (testing "matches the value"
        (given (into [] (r-sp-v/single-version-id-value-equals-filter
                         snapshot tid c-hash [(patient-ref "xa")])
                     [observation-svi])
          count := 1))

      (testing "matches if one of the values matches"
        (given (into [] (r-sp-v/single-version-id-value-equals-filter
                         snapshot tid c-hash [(patient-ref "x") (patient-ref "xa")])
                     [observation-svi])
          count := 1))

      (testing "doesn't match a longer value"
        (given (into [] (r-sp-v/single-version-id-value-equals-filter
                         snapshot tid c-hash [(patient-ref "xab")])
                     [observation-svi])
          count := 0)))))
