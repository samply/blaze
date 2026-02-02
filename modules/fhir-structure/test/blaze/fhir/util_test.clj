(ns blaze.fhir.util-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.structure-definition-repo]
   [blaze.fhir.util :as fu]
   [blaze.fhir.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]))

(st/instrument)
(ig/init-key :blaze.fhir/structure-definition-repo {})

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

(deftest coerce-params-test
  (testing "simple copy"
    (given (fu/coerce-params
            {"a" {:action :copy}}
            (fu/parameters "a" #fhir/string "b"))
      :a := "b")

    (testing "camelCase name"
      (given (fu/coerce-params
              {"fooBar" {:action :copy}}
              (fu/parameters "fooBar" #fhir/string "a"))
        :foo-bar := "a")))

  (testing "multiple copy"
    (given (fu/coerce-params
            {"a" {:action :copy :cardinality :many}}
            (fu/parameters "a" #fhir/string "b" "a" #fhir/string "c"))
      :as := ["b" "c"])

    (given (fu/coerce-params
            {"property" {:action :copy :cardinality :many}}
            (fu/parameters "property" #fhir/string "b" "property" #fhir/string "c"))
      :properties := ["b" "c"])

    (given (fu/coerce-params
            {"a" {:action :copy :coerce (comp #(str/split % #",") :value) :cardinality :many}}
            (fu/parameters "a" #fhir/string "b" "a" #fhir/string "c,d"))
      :as := ["b" "c" "d"])

    (given (fu/coerce-params
            {"a" {:action :copy :coerce (comp #(str/split % #",") :value) :cardinality :many}}
            (fu/parameters "a" #fhir/string "b,c" "a" #fhir/string "d"))
      :as := ["b" "c" "d"]))

  (testing "coercion"
    (given (fu/coerce-params
            {"a" {:action :copy :coerce (comp parse-long :value)}}
            (fu/parameters "a" #fhir/string "1"))
      :a := 1)

    (testing "error"
      (given (fu/coerce-params
              {"a" {:action :copy :coerce (constantly (ba/incorrect "msg-183537"))}}
              (fu/parameters "a" #fhir/string "1"))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid value for parameter `a`. msg-183537")))

  (testing "complex type copy"
    (given (fu/coerce-params
            {"a" {:action :copy-complex-type}}
            (fu/parameters "a" #fhir/Coding{:system #fhir/uri"a" :code #fhir/code"b"}))
      :a := #fhir/Coding{:system #fhir/uri"a" :code #fhir/code"b"}))

  (testing "resource copy"
    (given (fu/coerce-params
            {"a" {:action :copy-resource}}
            (fu/parameters "a" {:fhir/type :fhir/Patient :id "0"}))
      :a := {:fhir/type :fhir/Patient :id "0"}))

  (testing "unsupported param"
    (given (fu/coerce-params
            {"a" {}}
            (fu/parameters "a" #fhir/string "b"))
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported parameter `a`."))

  (testing "undefined param is ignored"
    (is (empty? (fu/coerce-params
                 {"a" {}}
                 (fu/parameters "b" #fhir/string "c"))))))
