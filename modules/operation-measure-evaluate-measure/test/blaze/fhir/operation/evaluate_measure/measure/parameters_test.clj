(ns blaze.fhir.operation.evaluate-measure.measure.parameters-test
  (:require
   [blaze.fhir.operation.evaluate-measure.measure.parameters :as parameters]
   [blaze.fhir.operation.evaluate-measure.measure.parameters-spec]
   [blaze.fhir.test-util]
   [blaze.fhir.util :as fu]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest effective-parameters-test
  (testing "without supplied parameters the defaults are returned"
    (testing "nil parameters"
      (is (= {"A" 1} (parameters/effective-parameters {"A" 1} nil))))

    (testing "empty parameters"
      (is (= {"A" 1} (parameters/effective-parameters
                      {"A" 1} {:fhir/type :fhir/Parameters})))))

  (testing "supplied parameters override defaults by name"
    (is (= {"Gender" "female"}
           (parameters/effective-parameters
            {"Gender" "male"}
            (fu/parameters "Gender" #fhir/string "female")))))

  (testing "library defaults still apply for parameters not supplied"
    (is (= {"A" 1 "B" "y"}
           (parameters/effective-parameters
            {"A" 1 "B" "x"}
            (fu/parameters "B" #fhir/string "y")))))

  (testing "primitive types"
    (testing "boolean"
      (given (parameters/effective-parameters
              {"P" nil} (fu/parameters "P" #fhir/boolean true))
        "P" := true))

    (testing "integer"
      (given (parameters/effective-parameters
              {"P" nil} (fu/parameters "P" #fhir/integer 42))
        "P" := 42))

    (testing "decimal"
      (given (parameters/effective-parameters
              {"P" nil} (fu/parameters "P" #fhir/decimal 3.14M))
        "P" := 3.14M))

    (testing "string"
      (given (parameters/effective-parameters
              {"P" nil} (fu/parameters "P" #fhir/string "str-113751"))
        "P" := "str-113751"))

    (testing "code"
      (given (parameters/effective-parameters
              {"P" nil} (fu/parameters "P" #fhir/code "code-113757"))
        "P" := "code-113757"))

    (testing "date"
      (given (parameters/effective-parameters
              {"P" nil} (fu/parameters "P" #fhir/date #system/date "2020"))
        "P" := #system/date "2020"))

    (testing "dateTime"
      (given (parameters/effective-parameters
              {"P" nil}
              (fu/parameters "P" #fhir/dateTime #system/date-time "2020"))
        "P" := #system/date-time "2020")))

  (testing "non-FHIR type"
    (given (st/with-instrument-disabled
             (parameters/effective-parameters {"P" nil} (fu/parameters "P" "foo")))
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported type of parameter `P`."))

  (testing "a repeated parameter becomes a List"
    (given (parameters/effective-parameters
            {"Codes" nil}
            (fu/parameters "Codes" [#fhir/code "a" #fhir/code "b"]))
      "Codes" := ["a" "b"]))

  (testing "non-FHIR type in repeated parameter"
    (given (st/with-instrument-disabled
             (parameters/effective-parameters {"P" nil} (fu/parameters "P" ["foo" "bar"])))
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported type of parameter `P`."))

  (testing "a parameter with parts becomes a Tuple"
    (is (= {"Range" {:low 1 :high 10}}
           (parameters/effective-parameters
            {"Range" nil}
            (fu/parameters "Range" [["low" #fhir/integer 1
                                     "high" #fhir/integer 10]])))))

  (testing "an unsupported type results in an anomaly"
    (given (parameters/effective-parameters
            {"Q" nil}
            (fu/parameters "Q" #fhir/Quantity{:value #fhir/decimal 1M}))
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported type `Quantity` of parameter `Q`."
      :fhir/issue := "not-supported"
      :fhir.issue/expression := "Q"))

  (testing "a resource-valued parameter results in an anomaly"
    (given (parameters/effective-parameters
            {"R" nil}
            {:fhir/type :fhir/Parameters
             :parameter
             [{:fhir/type :fhir.Parameters/parameter
               :name #fhir/string "R"
               :resource {:fhir/type :fhir/Patient :id "0"}}]})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported type `Patient` of parameter `R`."
      :fhir/issue := "not-supported"
      :fhir.issue/expression := "R"))

  (testing "an unknown parameter results in an anomaly"
    (given (parameters/effective-parameters
            {} (fu/parameters "Unknown" #fhir/string "x"))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Unknown parameter `Unknown`."
      :fhir/issue := "value"
      :fhir.issue/expression := "Unknown")))
