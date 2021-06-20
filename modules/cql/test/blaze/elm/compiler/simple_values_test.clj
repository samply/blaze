(ns blaze.elm.compiler.simple-values-test
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 1.1 Literal
(deftest compile-literal-test
  (testing "Boolean Literal"
    (are [elm res] (= res (c/compile {} elm))
      #elm/boolean"true" true
      #elm/boolean"false" false))

  (testing "Decimal Literal"
    (are [elm res] (= res (c/compile {} elm))
      #elm/decimal"-1" -1M
      #elm/decimal"0" 0M
      #elm/decimal"1" 1M

      #elm/decimal"-0.1" -0.1M
      #elm/decimal"0.0" 0M
      #elm/decimal"0.1" 0.1M

      #elm/decimal"0.000000001" 0M
      #elm/decimal"0.000000005" 1E-8M))

  (testing "Integer Literal"
    (are [elm res] (= res (c/compile {} elm))
      #elm/integer"-1" -1
      #elm/integer"0" 0
      #elm/integer"1" 1))

  (testing "Unknown Literal"
    (is (nil? (c/compile {} {:type "Literal"
                             :valueType "foo"})))
    (is (thrown-anom? ::anom/unsupported
                      (c/compile {} {:type "Literal"
                                     :valueType "foo"
                                     :value "bar"})))
    (is (thrown-anom? ::anom/unsupported
                      (c/compile {} {:type "Literal"
                                     :valueType "{urn:hl7-org:elm-types:r1}foo"
                                     :value "bar"})))))
