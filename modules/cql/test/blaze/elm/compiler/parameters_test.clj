(ns blaze.elm.compiler.parameters-test
  (:require
    [blaze.elm.code-spec]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.parameters :refer [->ParameterRef]]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]))


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
      (is (thrown-anom? ::anom/incorrect (c/compile context #elm/parameter-ref"parameter-def-103701")))))

  (testing "value not found"
    (let [context
          {:library
           {:parameters
            {:def
             [{:name "parameter-def-111045"}]}}}
          expr (c/compile context #elm/parameter-ref"parameter-def-111045")]
      (is (thrown-anom? ::anom/incorrect (core/-eval expr {} nil nil))))))
