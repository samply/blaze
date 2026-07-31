(ns blaze.fhir.writing-context-test
  (:require
   [blaze.fhir.writing-context]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [blaze.fhir.writing PropertyHandler PropertyIndex StringPropertyHandler]
   [com.fasterxml.jackson.core.io SerializedString]))

(st/instrument)
(set! *warn-on-reflection* true)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.fhir/writing-context nil}
      :key := :blaze.fhir/writing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir/writing-context {}}
      :key := :blaze.fhir/writing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))))

  (testing "invalid structure-definition-repo"
    (given-failed-system {:blaze.fhir/writing-context {:structure-definition-repo ::invalid}}
      :key := :blaze.fhir/writing-context
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/structure-definition-repo]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def ^:private field-name (SerializedString. "field-name"))

(defn- property-index ^PropertyIndex [keys]
  (PropertyIndex.
   (into-array PropertyHandler (map #(StringPropertyHandler. % field-name) keys))))

(def ^:private distinct-keys
  (gen/vector-distinct gen/keyword {:min-elements 1 :max-elements 64}))

(deftest property-index-test
  (testing "every key is found at the index of its property handler"
    (satisfies-prop 1000
      (prop/for-all [keys distinct-keys]
        (let [index (property-index keys)]
          (every? (fn [[i key]] (= i (.get index key))) (map-indexed vector keys))))))

  (testing "keys without a property handler are not found"
    (satisfies-prop 1000
      (prop/for-all [keys distinct-keys
                     other-keys distinct-keys]
        (let [index (property-index keys)]
          (every? #(= -1 (.get index %)) (remove (set keys) other-keys))))))

  (testing "non-keyword keys are not found"
    (let [index (property-index [:foo])]
      (is (= -1 (.get index "foo")))
      (is (= -1 (.get index nil)))))

  (testing "duplicate keys are rejected"
    (is (thrown? IllegalArgumentException (property-index [:foo :foo])))))
