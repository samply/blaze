(ns blaze.fhir.util-test
  (:require
   [blaze.fhir.structure-definition-repo]
   [blaze.fhir.util :as fu]
   [blaze.fhir.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]))

(st/instrument)
(ig/init {:blaze.fhir/structure-definition-repo {}})

(test/use-fixtures :each tu/fixture)

(deftest parameters-test
  (given (fu/parameters)
    :fhir/type := :fhir/Parameters
    :parameter :? empty?)

  (given (fu/parameters "foo" #fhir/string "bar")
    :fhir/type := :fhir/Parameters
    [:parameter 0 :name] := #fhir/string "foo"
    [:parameter 0 :value] := #fhir/string "bar")

  (given (fu/parameters "foo" nil)
    :fhir/type := :fhir/Parameters
    :parameter :? empty?)

  (given (fu/parameters "foo" {:fhir/type :fhir/ValueSet})
    :fhir/type := :fhir/Parameters
    [:parameter 0 :name] := #fhir/string "foo"
    [:parameter 0 :resource] := {:fhir/type :fhir/ValueSet}))

(deftest subsetted-test
  (are [coding] (fu/subsetted? coding)
    {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
     :code #fhir/code "SUBSETTED"}
    {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
     :code #fhir/code {:id "foo" :value "SUBSETTED"}}
    {:system #fhir/uri {:id "foo" :value "http://terminology.hl7.org/CodeSystem/v3-ObservationValue"}
     :code #fhir/code "SUBSETTED"}
    fu/subsetted)

  (are [coding] (not (fu/subsetted? coding))
    {:code #fhir/code "SUBSETTED"}
    {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/v3-ObservationValue"}))

(deftest version-cmp-test
  (is (zero? (fu/version-cmp nil nil)))
  (is (= -1 (fu/version-cmp nil "")))
  (is (= 1 (fu/version-cmp "" nil)))
  (is (zero? (fu/version-cmp "" "")))
  (is (= -1 (fu/version-cmp "1" "2")))
  (is (zero? (fu/version-cmp "1" "1")))
  (is (= 1 (fu/version-cmp "2" "1")))
  (is (= -1 (fu/version-cmp "a" "b")))
  (is (zero? (fu/version-cmp "a" "a")))
  (is (= 1 (fu/version-cmp "b" "a")))
  (is (= -1 (fu/version-cmp "1" "a")))
  (is (= 1 (fu/version-cmp "a" "1")))
  (is (= -1 (fu/version-cmp "1.2" "1.10")))
  (is (zero? (fu/version-cmp "1.2" "1.2")))
  (is (= 1 (fu/version-cmp "1.10" "1.2")))
  (is (= -1 (fu/version-cmp "1" "1.1")))
  (is (= 1 (fu/version-cmp "1.1" "1"))))

(deftest sort-by-priority-test
  (testing "empty"
    (is (empty? (fu/sort-by-priority []))))

  (testing "one code-system"
    (is (= (fu/sort-by-priority [{:fhir/type :fhir/CodeSystem}])
           [{:fhir/type :fhir/CodeSystem}])))

  (testing "two code-systems"
    (testing "active comes first"
      (is (= (fu/sort-by-priority
              [{:fhir/type :fhir/CodeSystem
                :status #fhir/code "draft"}
               {:fhir/type :fhir/CodeSystem
                :status #fhir/code "active"}])
             [{:fhir/type :fhir/CodeSystem
               :status #fhir/code "active"}
              {:fhir/type :fhir/CodeSystem
               :status #fhir/code "draft"}])))

    (testing "without status comes last"
      (is (= (fu/sort-by-priority
              [{:fhir/type :fhir/CodeSystem}
               {:fhir/type :fhir/CodeSystem
                :status #fhir/code "draft"}])
             [{:fhir/type :fhir/CodeSystem
               :status #fhir/code "draft"}
              {:fhir/type :fhir/CodeSystem}])))

    (testing "active 1.0.0 comes before draft 2.0.0-alpha.1"
      (is (= (fu/sort-by-priority
              [{:fhir/type :fhir/CodeSystem
                :version #fhir/string "2.0.0-alpha.1"
                :status #fhir/code "draft"}
               {:fhir/type :fhir/CodeSystem
                :version #fhir/string "1.0.0"
                :status #fhir/code "active"}])
             [{:fhir/type :fhir/CodeSystem
               :version #fhir/string "1.0.0"
               :status #fhir/code "active"}
              {:fhir/type :fhir/CodeSystem
               :version #fhir/string "2.0.0-alpha.1"
               :status #fhir/code "draft"}])))

    (testing "newest comes first"
      (is (= (fu/sort-by-priority
              [(with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 1}})
               (with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 2}})])
             [(with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 2}})
              (with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 1}})])))

    (testing "resource without t (external resource) comes first"
      (is (= (fu/sort-by-priority
              [(with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 1}})
               {:fhir/type :fhir/CodeSystem}])
             [{:fhir/type :fhir/CodeSystem}
              (with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 1}})])))

    (testing "largest id comes first"
      (is (= (fu/sort-by-priority
              [{:fhir/type :fhir/CodeSystem :id "1"}
               {:fhir/type :fhir/CodeSystem :id "2"}])
             [{:fhir/type :fhir/CodeSystem :id "2"}
              {:fhir/type :fhir/CodeSystem :id "1"}])))))
