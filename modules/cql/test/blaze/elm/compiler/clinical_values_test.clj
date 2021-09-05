(ns blaze.elm.compiler.clinical-values-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.elm.code-spec]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.clinical-values]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.date-time :as date-time]
    [blaze.elm.literal]
    [blaze.elm.literal-spec]
    [blaze.elm.quantity :as quantity]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [clojure.test.check.properties :as prop]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [blaze.elm.code Code]
    [blaze.elm.date_time Period]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 3.1. Code
;;
;; The Code type represents a literal code selector.
(deftest compile-code-test
  (testing "without version"
    (let [context
          {:library
           {:codeSystems
            {:def [{:name "sys-def-115852" :id "system-115910"}]}}}]
      (given (c/compile context #elm/code["sys-def-115852" "code-115927"])
        type := Code
        :system := "system-115910"
        :code := "code-115927")))

  (testing "with version"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-120434"
               :id "system-120411"
               :version "version-120408"}]}}}]
      (given (c/compile context #elm/code["sys-def-120434" "code-120416"])
        type := Code
        :system := "system-120411"
        :version := "version-120408"
        :code := "code-120416")))

  (testing "missing code system"
    (let [context {:library {:codeSystems {:def []}}}]
      (given (ba/try-anomaly (c/compile context #elm/code["sys-def-112249" "code-112253"]))
        ::anom/category := ::anom/not-found
        ::anom/message := "Can't find the code system `sys-def-112249`."))))


;; 3.2. CodeDef
;;
;; Only use indirectly through CodeRef.


;; 3.3. CodeRef
;;
;; The CodeRef expression allows a previously defined code to be referenced
;; within an expression.
(deftest compile-code-ref-test
  (testing "without version"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-125149"
               :id "system-name-125213"}]}
            :codes
            {:def
             [{:name "code-def-125054"
               :id "code-125340"
               :codeSystem {:name "sys-def-125149"}}]}}}]
      (given (c/compile context #elm/code-ref"code-def-125054")
        type := Code
        :system := "system-name-125213"
        :code := "code-125340")))

  (testing "with version"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-125149"
               :id "system-name-125213"
               :version "version-125222"}]}
            :codes
            {:def
             [{:name "code-def-125054"
               :id "code-125354"
               :codeSystem {:name "sys-def-125149"}}]}}}]
      (given (c/compile context #elm/code-ref"code-def-125054")
        type := Code
        :system := "system-name-125213"
        :version := "version-125222"
        :code := "code-125354"))))


;; 3.4. CodeSystemDef
;;
;; Only used indirectly through Code and CodeDef.


;; 3.5. CodeSystemRef
;;
;; Only used indirectly through Code and CodeDef.


;; 3.6. Concept
;;
;; The Concept type represents a literal concept selector.
;; TODO


;; 3.9. Quantity
;;
;; The Quantity type defines a clinical quantity. For example, the quantity 10
;; days or 30 mmHg. The value is a decimal, while the unit is expected to be a
;; valid UCUM unit.
(deftest compile-quantity-test
  (testing "Examples"
    (are [elm res] (= res (c/compile {} elm))
      {:type "Quantity"} nil
      #elm/quantity[1] (quantity/quantity 1 "")
      #elm/quantity[1 "year"] (date-time/period 1 0 0)
      #elm/quantity[2 "years"] (date-time/period 2 0 0)
      #elm/quantity[1 "month"] (date-time/period 0 1 0)
      #elm/quantity[2 "months"] (date-time/period 0 2 0)
      #elm/quantity[1 "week"] (date-time/period 0 0 (* 7 24 60 60 1000))
      #elm/quantity[2 "weeks"] (date-time/period 0 0 (* 2 7 24 60 60 1000))
      #elm/quantity[1 "day"] (date-time/period 0 0 (* 24 60 60 1000))
      #elm/quantity[2 "days"] (date-time/period 0 0 (* 2 24 60 60 1000))
      #elm/quantity[1 "hour"] (date-time/period 0 0 (* 60 60 1000))
      #elm/quantity[2 "hours"] (date-time/period 0 0 (* 2 60 60 1000))
      #elm/quantity[1 "minute"] (date-time/period 0 0 (* 60 1000))
      #elm/quantity[2 "minutes"] (date-time/period 0 0 (* 2 60 1000))
      #elm/quantity[1 "second"] (date-time/period 0 0 1000)
      #elm/quantity[2 "seconds"] (date-time/period 0 0 2000)
      #elm/quantity[1 "millisecond"] (date-time/period 0 0 1)
      #elm/quantity[2 "milliseconds"] (date-time/period 0 0 2)
      #elm/quantity[1 "s"] (quantity/quantity 1 "s")
      #elm/quantity[1 "cm2"] (quantity/quantity 1 "cm2")))

  (testing "Periods"
    (tu/satisfies-prop 100
      (prop/for-all [period (s/gen :elm/period)]
        (#{BigDecimal Period} (type (core/-eval (c/compile {} period) {} nil nil)))))))
