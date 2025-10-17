(ns blaze.fhir.operation.evaluate-measure.measure.util-test
  (:require
   [blaze.anomaly-spec]
   [blaze.fhir.operation.evaluate-measure.measure.util :as u]
   [blaze.fhir.operation.evaluate-measure.measure.util-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest expression-test
  (testing "missing criteria"
    (given (u/expression-name (constantly "path-184642") nil)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing criteria."
      :fhir/issue := "required"
      :fhir.issue/expression := "path-184642"))

  (testing "unsupported language"
    (given (u/expression-name (constantly "path-184706")
                              #fhir/Expression{:language #fhir/code "lang-184851"})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported language `lang-184851`."
      :fhir/issue := "not-supported"
      :fhir.issue/expression := "path-184706.criteria.language"))

  (testing "missing expression"
    (given (u/expression-name (constantly "path-184642")
                              #fhir/Expression{:language #fhir/code "text/cql-identifier"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing expression."
      :fhir/issue := "required"
      :fhir.issue/expression := "path-184642.criteria"))

  (testing "works with `text/cql-identifier`"
    (satisfies-prop 10
      (prop/for-all [expression gen/string]
        (= expression (u/expression-name (constantly "foo")
                                         (type/expression
                                          {:language #fhir/code "text/cql-identifier"
                                           :expression (type/string expression)}))))))

  (testing "works with `text/cql`"
    (satisfies-prop 10
      (prop/for-all [expression gen/string]
        (= expression (u/expression-name (constantly "foo")
                                         (type/expression
                                          {:language #fhir/code "text/cql"
                                           :expression (type/string expression)})))))))

(defn- cql-expression [expr]
  (type/expression {:language #fhir/code "text/cql-identifier"
                    :expression (type/string expr)}))

(deftest cql-definition-names-test
  (are [measure names] (= names (u/expression-names measure))
    {:fhir/type :fhir/Measure :id "0"
     :url #fhir/uri "measure-155502"
     :library [#fhir/canonical "0"]
     :group
     [{:fhir/type :fhir.Measure/group
       :population
       [{:fhir/type :fhir.Measure.group/population
         :criteria (cql-expression "InInitialPopulation")}]
       :stratifier
       [{:fhir/type :fhir.Measure.group/stratifier
         :criteria (cql-expression "Gender")}]}]}
    #{"InInitialPopulation"
      "Gender"}

    {:fhir/type :fhir/Measure :id "0"
     :url #fhir/uri "measure-155502"
     :library [#fhir/canonical "0"]
     :group
     [{:fhir/type :fhir.Measure/group
       :population
       [{:fhir/type :fhir.Measure.group/population
         :criteria (cql-expression "InInitialPopulation")}]
       :stratifier
       [{:fhir/type :fhir.Measure.group/stratifier
         :component
         [{:fhir/type :fhir.Measure.group.stratifier/component
           :criteria (cql-expression "AgeClass")}
          {:fhir/type :fhir.Measure.group.stratifier/component
           :criteria (cql-expression "Gender")}]}]}]}
    #{"InInitialPopulation"
      "AgeClass"
      "Gender"}))
