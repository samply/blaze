(ns blaze.db.impl.index.resource-handle-test
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.resource-handle-spec]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.properties :as prop]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private hash
  (hash/generate {:fhir/type :fhir/Patient :id "0"}))

(defn- resource-handle
  ([tid id]
   (resource-handle tid id 0))
  ([tid id t]
   (resource-handle tid id t hash))
  ([tid id t hash]
   (resource-handle tid id t hash :create))
  ([tid id t hash op]
   (rh/->ResourceHandle tid id t hash 0 op)))

(deftest state->num-changes-test
  (are [state num-changes] (= num-changes
                              (rh/state->num-changes state)
                              (apply rh/state->num-changes [state]))
    0 0
    256 1
    512 2))

(deftest deleted-test
  (are [rh] (and (rh/deleted? rh) (apply rh/deleted? [rh]))
    (resource-handle 0 "0" 0 hash :delete)))

(deftest tid-test
  (satisfies-prop 100
    (prop/for-all [tid (s/gen :blaze.db/tid)]
      (let [rh (resource-handle tid "foo")]
        (= tid (:tid rh) (rh/tid rh) (apply rh/tid [rh]))))))

(deftest id-test
  (satisfies-prop 100
    (prop/for-all [id (s/gen :blaze.resource/id)]
      (let [rh (resource-handle 0 id)]
        (= id (:id rh) (rh/id rh) (apply rh/id [rh]))))))

(deftest t-test
  (satisfies-prop 100
    (prop/for-all [t (s/gen :blaze.db/t)]
      (let [rh (resource-handle 0 "foo" t)]
        (= t (:t rh) (rh/t rh) (apply rh/t [rh]))))))

(deftest hash-test
  (satisfies-prop 100
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (let [rh (resource-handle 0 "foo" 0 hash)]
        (= hash (:hash rh) (rh/hash rh) (apply rh/hash [rh]))))))

(deftest reference-test
  (satisfies-prop 100
    (prop/for-all [id (s/gen :blaze.resource/id)]
      (let [rh (resource-handle 1495153489 id)]
        (= (str "Condition/" id)
           (rh/reference rh)
           (apply rh/reference [rh]))))))

(deftest not-found-key-test
  (is (nil? (:foo (resource-handle 0 "foo"))))
  (is (= ::not-found (:foo (resource-handle 0 "foo") ::not-found))))

(deftest equals-test
  (satisfies-prop 100
    (prop/for-all [tid (s/gen :blaze.db/tid)
                   id (s/gen :blaze.resource/id)
                   t (s/gen :blaze.db/t)]

      (testing "same instance"
        (let [rh (resource-handle tid id t)]
          (= rh rh)))

      (testing "separate instances"
        (let [rh-1 (resource-handle tid id t)
              rh-2 (resource-handle tid id t)]
          (= rh-1 rh-2))))))

(deftest to-string-test
  (satisfies-prop 100
    (prop/for-all [id (s/gen :blaze.resource/id)
                   t (s/gen :blaze.db/t)]

      (= (format "Condition[id = %s, t = %d]" id t)
         (str (resource-handle 1495153489 id t))))))
