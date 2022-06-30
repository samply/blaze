(ns blaze.fhir.operation.evaluate-measure.measure.util-test
  (:require
    [blaze.anomaly-spec]
    [blaze.fhir.operation.evaluate-measure.measure.util :as u]
    [blaze.test-util :refer [satisfies-prop]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest expression-test
  (testing "missing criteria"
    (given (u/expression (constantly "path-184642") nil)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing criteria."
      :fhir/issue := "required"
      :fhir.issue/expression := "path-184642"))

  (testing "unsupported language"
    (given (u/expression (constantly "path-184706")
                         {:language #fhir/code"lang-184851"})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported language `lang-184851`."
      :fhir/issue := "not-supported"
      :fhir.issue/expression := "path-184706.criteria.language"))

  (testing "missing expression"
    (given (u/expression (constantly "path-184642")
                         {:language #fhir/code"text/cql-identifier"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing expression."
      :fhir/issue := "required"
      :fhir.issue/expression := "path-184642.criteria"))

  (testing "works with `text/cql-identifier`"
    (satisfies-prop 10
      (prop/for-all [expression gen/string]
        (= expression (u/expression (constantly "foo")
                                    {:language #fhir/code"text/cql-identifier"
                                     :expression expression})))))

  (testing "works with `text/cql`"
    (satisfies-prop 10
      (prop/for-all [expression gen/string]
        (= expression (u/expression (constantly "foo")
                                    {:language #fhir/code"text/cql"
                                     :expression expression}))))))
