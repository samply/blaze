(ns blaze.db.impl.index.resource-handle-test
  (:refer-clojure :exclude [hash])
  (:require
    [blaze.db.impl.codec :as codec]
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
  ([tid did]
   (resource-handle tid did 0))
  ([tid did t]
   (resource-handle tid did t hash))
  ([tid did t hash]
   (resource-handle tid did t hash :create "0"))
  ([tid did t hash op id]
   (rh/->ResourceHandle tid did t hash 0 op id)))


(deftest state->num-changes-test
  (are [state num-changes]
    (= num-changes
       (rh/state->num-changes state)
       (apply rh/state->num-changes [state]))
    0 0
    256 1
    512 2))


(deftest deleted-test
  (are [rh] (and (rh/deleted? rh) (apply rh/deleted? [rh]))
    (resource-handle 0 0 0 hash :delete "0")))


(deftest tid-test
  (satisfies-prop 100
    (prop/for-all [tid (s/gen :blaze.db/tid)]
      (let [rh (resource-handle tid 0)]
        (= tid (:tid rh) (rh/tid rh) (apply rh/tid [rh]))))))


(deftest did-test
  (satisfies-prop 100
    (prop/for-all [did (s/gen :blaze.db/did)]
      (let [rh (resource-handle 0 did)]
        (= did (:did rh) (rh/did rh) (apply rh/did [rh]))))))


(deftest t-test
  (satisfies-prop 100
    (prop/for-all [t (s/gen :blaze.db/t)]
      (let [rh (resource-handle 0 0 t)]
        (= t (:t rh) (rh/t rh) (apply rh/t [rh]))))))


(deftest hash-test
  (satisfies-prop 100
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (let [rh (resource-handle 0 0 0 hash)]
        (= hash (:hash rh) (rh/hash rh) (apply rh/hash [rh]))))))


(deftest id-test
  (satisfies-prop 100
    (prop/for-all [id (s/gen :blaze.resource/id)]
      (let [rh (resource-handle 0 0 0 hash :create id)]
        (= id (:id rh) (rh/id rh) (apply rh/id [rh]))))))


(deftest reference-test
  (satisfies-prop 100
    (prop/for-all [id (s/gen :blaze.resource/id)]
      (let [rh (resource-handle 1495153489 0 0 hash :create id)]
        (= (str "Condition/" id)
           (rh/reference rh)
           (apply rh/reference [rh]))))))


(deftest not-found-key-test
  (is (nil? (:foo (resource-handle 0 0))))
  (is (= ::not-found (:foo (resource-handle 0 0) ::not-found))))


(deftest equals-test
  (satisfies-prop 100
    (prop/for-all [tid (s/gen :blaze.db/tid)
                   did (s/gen :blaze.db/did)
                   t (s/gen :blaze.db/t)]

      (testing "same instance"
        (let [rh (resource-handle tid did t)]
          (= rh rh)))

      (testing "separate instances"
        (let [rh-1 (resource-handle tid did t)
              rh-2 (resource-handle tid did t)]
          (= rh-1 rh-2))))))


(deftest toString-test
  (is (= "Patient/182457" (str (resource-handle (codec/tid "Patient") 0 0 hash :put "182457")))))
