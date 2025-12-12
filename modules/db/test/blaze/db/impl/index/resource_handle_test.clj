(ns blaze.db.impl.index.resource-handle-test
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle]
   [blaze.db.impl.index.resource-handle-spec]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.properties :as prop])
  (:import
   [blaze.db.impl.index ResourceHandle]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- resource-handle [type id t]
  (let [fhir-type (keyword "fhir" type)]
    (ResourceHandle. fhir-type (codec/tid type) id t
                     (hash/generate {:fhir/type fhir-type :id id}) 0 nil)))

(deftest state->num-changes-test
  (are [state num-changes] (= num-changes (ResourceHandle/numChanges state))
    0 0
    256 1
    512 2))

(deftest not-found-key-test
  (is (nil? (:foo (resource-handle "Patient" "foo" 0))))
  (is (= ::not-found (:foo (resource-handle "Patient" "foo" 0) ::not-found))))

(deftest equals-test
  (satisfies-prop 100
    (prop/for-all [type (sg/elements ["Patient" "Observation" "Condition"])
                   id (s/gen :blaze.resource/id)
                   t (s/gen :blaze.db/t)]

      (testing "same instance"
        (let [rh (resource-handle type id t)]
          (= rh rh)))

      (testing "separate instances"
        (let [rh-1 (resource-handle type id t)
              rh-2 (resource-handle type id t)]
          (= rh-1 rh-2))))))

(deftest to-string-test
  (satisfies-prop 100
    (prop/for-all [type (sg/elements ["Patient" "Observation" "Condition"])
                   id (s/gen :blaze.resource/id)
                   t (s/gen :blaze.db/t)]

      (= (format "%s[id = %s, t = %d]" type id t)
         (str (resource-handle type id t))))))
