(ns blaze.elm.compiler.simple-values-test
  "1. Simple Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.simple-values]
    [blaze.elm.compiler.test-util :as ctu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)
(ctu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 1.1 Literal
;;
;; The Literal type defines a single scalar value. For example, the literal 5,
;; the boolean value true or the string "antithrombotic".
(deftest compile-literal-test
  (testing "Boolean Literal"
    (are [elm res] (let [expr (c/compile {} elm)] (= res expr (c/form expr)))
      #elm/boolean "true" true
      #elm/boolean "false" false))

  (testing "Decimal Literal"
    (are [elm res] (let [expr (c/compile {} elm)] (= res expr (c/form expr)))
      #elm/decimal "-1" -1M
      #elm/decimal "0" 0M
      #elm/decimal "1" 1M

      #elm/decimal "-0.1" -0.1M
      #elm/decimal "0.0" 0M
      #elm/decimal "0.1" 0.1M

      #elm/decimal "0.000000001" 0M
      #elm/decimal "0.000000005" 1E-8M

      #elm/decimal "-99999999999999999999.99999999" -99999999999999999999.99999999M
      #elm/decimal "99999999999999999999.99999999" 99999999999999999999.99999999M)

    (testing "failure"
      (given (ba/try-anomaly (c/compile {} #elm/decimal "x"))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Incorrect decimal literal `x`.")))

  (testing "Long Literal"
    (are [elm res] (let [expr (c/compile {} elm)] (= res expr (c/form expr)))
      #elm/long "-1" -1
      #elm/long "0" 0
      #elm/long "1" 1)

    (testing "failure"
      (given (ba/try-anomaly (c/compile {} #elm/long "x"))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Incorrect long literal `x`.")))

  (testing "Integer Literal"
    (are [elm res] (let [expr (c/compile {} elm)] (= res expr (c/form expr)))
      #elm/integer "-1" -1
      #elm/integer "0" 0
      #elm/integer "1" 1)

    (testing "failure"
      (doseq [s ["x" "2147483649" "-2147483649"]]
        (given (ba/try-anomaly (c/compile {} (elm/integer s)))
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Incorrect integer literal `%s`." s)))))

  (testing "Unknown Literal"
    (is (nil? (c/compile {} {:type "Literal"
                             :valueType "foo"})))

    (given (ba/try-anomaly (c/compile {} {:type "Literal"
                                          :valueType "foo"
                                          :value "bar"}))
      ::anom/category := ::anom/unsupported)

    (given (ba/try-anomaly (c/compile {} {:type "Literal"
                                          :valueType "{urn:hl7-org:elm-types:r1}foo"
                                          :value "bar"}))
      ::anom/category := ::anom/unsupported)))
