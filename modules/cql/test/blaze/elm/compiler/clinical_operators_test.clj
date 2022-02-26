(ns blaze.elm.compiler.clinical-operators-test
  "23. Clinical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.clinical-operators]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; TODO 23.1. AnyInCodeSystem


;; TODO 23.2. AnyInValueSet


;; 23.3. CalculateAge
;;
;; Normalized to CalculateAgeAt
(deftest compile-calculate-age-test
  (tu/unsupported-unary-operand "CalculateAge"))


;; 23.4. CalculateAgeAt
;;
;; Calculates the age in the specified precision of a person born on a given
;; date, as of another given date.
;;
;; The CalculateAgeAt operator has two signatures: (Date, Date) (DateTime,
;; DateTime)
;;
;; For the Date overload, precision must be one of year, month, week, or day,
;; and the result is the number of whole calendar periods that have elapsed
;; between the first date and the second date.
;;
;; For the DateTime overload, the result is the number of whole calendar periods
;; that have elapsed between the first datetime and the second datetime.
(deftest compile-calculate-age-at-test
  (testing "Year"
    (are [elm res] (= res (core/-eval (c/compile {} elm) {:now tu/now} nil nil))
      {:type "CalculateAgeAt" :operand [#elm/date "2018" #elm/date "2019"]
       :precision "Year"}
      1
      {:type "CalculateAgeAt" :operand [#elm/date "2018" #elm/date "2018"]
       :precision "Year"}
      0

      {:type "CalculateAgeAt" :operand [#elm/date "2018" #elm/date "2018"]
       :precision "Month"}
      nil))

  (tu/testing-binary-null elm/calculate-age-at #elm/date "2018"))


;; 23.5. Equal


;; 23.6. Equivalent


;; TODO 23.7. InCodeSystem


;; TODO 23.8. InValueSet


;; 23.9. Not Equal


;; TODO 23.10. SubsumedBy


;; TODO 23.11. Subsumes
