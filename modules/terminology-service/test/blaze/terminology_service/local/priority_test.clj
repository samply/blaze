(ns blaze.terminology-service.local.priority-test
  (:require
   [blaze.fhir.test-util]
   [blaze.terminology-service.local.priority :as priority]
   [blaze.terminology-service.local.priority-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest sort-by-priority-test
  (testing "empty"
    (is (empty? (priority/sort-by-priority []))))

  (testing "one code-system"
    (is (= (priority/sort-by-priority [{:fhir/type :fhir/CodeSystem}])
           [{:fhir/type :fhir/CodeSystem}])))

  (testing "two code-systems"
    (testing "active comes first"
      (is (= (priority/sort-by-priority
              [{:fhir/type :fhir/CodeSystem
                :status #fhir/code"draft"}
               {:fhir/type :fhir/CodeSystem
                :status #fhir/code"active"}])
             [{:fhir/type :fhir/CodeSystem
               :status #fhir/code"active"}
              {:fhir/type :fhir/CodeSystem
               :status #fhir/code"draft"}])))

    (testing "without status comes last"
      (is (= (priority/sort-by-priority
              [{:fhir/type :fhir/CodeSystem}
               {:fhir/type :fhir/CodeSystem
                :status #fhir/code"draft"}])
             [{:fhir/type :fhir/CodeSystem
               :status #fhir/code"draft"}
              {:fhir/type :fhir/CodeSystem}])))

    (testing "active 1.0.0 comes before draft 2.0.0-alpha.1"
      (is (= (priority/sort-by-priority
              [{:fhir/type :fhir/CodeSystem
                :version #fhir/string"2.0.0-alpha.1"
                :status #fhir/code"draft"}
               {:fhir/type :fhir/CodeSystem
                :version #fhir/string"1.0.0"
                :status #fhir/code"active"}])
             [{:fhir/type :fhir/CodeSystem
               :version #fhir/string"1.0.0"
               :status #fhir/code"active"}
              {:fhir/type :fhir/CodeSystem
               :version #fhir/string"2.0.0-alpha.1"
               :status #fhir/code"draft"}])))))
