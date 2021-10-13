(ns blaze.elm.compiler.parameters-test
  "7. Parameters

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba]
    [blaze.elm.code-spec]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.parameters :refer [->ParameterRef]]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 7.2. ParameterRef
;;
;; The ParameterRef expression allows the value of a parameter to be referenced
;; as part of an expression.
(deftest compile-parameter-ref-test
  (testing "found"
    (let [context
          {:library
           {:parameters
            {:def
             [{:name "parameter-def-101820"}]}}}]
      (is (= (->ParameterRef "parameter-def-101820")
             (c/compile context #elm/parameter-ref"parameter-def-101820")))))

  (testing "definition not found"
    (let [context {:library {}}]
      (given (ba/try-anomaly (c/compile context #elm/parameter-ref"parameter-def-103701"))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Parameter definition `parameter-def-103701` not found."
        :context := context)))

  (testing "value not found"
    (let [context
          {:library
           {:parameters
            {:def
             [{:name "parameter-def-111045"}]}}}
          expr (c/compile context #elm/parameter-ref"parameter-def-111045")]
      (given (ba/try-anomaly (core/-eval expr {} nil nil))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Value of parameter `parameter-def-111045` not found."
        :context := {}))))
