(ns blaze.fhir.operation.evaluate-measure.measure.util-test
  (:require
    [blaze.anomaly-spec]
    [blaze.fhir.operation.evaluate-measure.measure.util :as u]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
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
                         {:language #fhir/code"text/cql"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing expression."
      :fhir/issue := "required"
      :fhir.issue/expression := "path-184642.criteria")))
