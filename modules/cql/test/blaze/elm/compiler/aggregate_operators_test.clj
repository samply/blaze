(ns blaze.elm.compiler.aggregate-operators-test
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.aggregate-operators]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(st/instrument)
(tu/instrument-compile)


(defn fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 21.1. AllTrue
;;
;; The AllTrue operator returns true if all the non-null elements in source are
;; true.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, true is returned.
;;
;; If the source is null, the result is true.
(deftest compile-all-true-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/all-true source)) {} nil nil))
      #elm/list [#elm/boolean"true" #elm/boolean"false"] false
      #elm/list [#elm/boolean"false"] false
      #elm/list [#elm/boolean"true"] true

      #elm/list [{:type "Null"}] true
      #elm/list [] true
      {:type "Null"} true)))


;; 21.2. AnyTrue
;;
;; The AnyTrue operator returns true if any non-null element in source is true.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, false is returned.
;;
;; If the source is null, the result is false.
(deftest compile-any-true-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/any-true source)) {} nil nil))
      #elm/list [#elm/boolean"true" #elm/boolean"false"] true
      #elm/list [#elm/boolean"false"] false
      #elm/list [#elm/boolean"true"] true

      #elm/list [{:type "Null"}] false
      #elm/list [] false
      {:type "Null"} false)))


;; 21.3. Avg
;;
;; The Avg operator returns the average of the non-null elements in source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the source is null, the result is null.
(deftest compile-avg-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/avg source)) {} nil nil))
      #elm/list [#elm/decimal"1" #elm/decimal"2"] 1.5M
      #elm/list [#elm/integer"1" #elm/integer"2"] 1.5M
      #elm/list [#elm/integer"1"] 1M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.4. Count
;;
;; The Count operator returns the number of non-null elements in the source.
;;
;; If a path is specified the count returns the number of elements that have a
;; value for the property specified by the path.
;;
;; If the list is empty the result is 0.
;;
;; If the list is null the result is 0.
(deftest compile-count-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/count source)) {} nil nil))
      #elm/list [#elm/integer"1"] 1
      #elm/list [#elm/integer"1" #elm/integer"1"] 2

      #elm/list [{:type "Null"}] 0
      #elm/list [] 0
      {:type "Null"} 0)))


;; 21.5. GeometricMean
;;
;; The GeometricMean operator returns the geometric mean of the non-null
;; elements in source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the source is null, the result is null.
(deftest compile-geometric-mean-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/geometric-mean source)) {} nil nil))
      #elm/list [#elm/decimal"2" #elm/decimal"8"] 4M
      #elm/list [#elm/integer"2" #elm/integer"8"] 4M
      #elm/list [#elm/integer"1"] 1M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.6. Product
;;
;; The Product operator returns the geometric product of non-null elements in
;; the source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the list is null, the result is null.
(deftest compile-product-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/product source)) {} nil nil))
      #elm/list [#elm/decimal"2" #elm/decimal"8"] 16M
      #elm/list [#elm/integer"2" #elm/integer"8"] 16
      #elm/list [#elm/integer"1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.7. Max
;;
;; The Max operator returns the maximum element in the source. Comparison
;; semantics are defined by the comparison operators for the type of the values
;; being aggregated.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the source is null, the result is null.
(deftest compile-max-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/max source)) {} nil nil))
      #elm/list [#elm/decimal"2" #elm/decimal"8"] 8M
      #elm/list [#elm/integer"2" #elm/integer"8"] 8
      #elm/list [#elm/integer"1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.8. Median
;;
;; The Median operator returns the median of the elements in source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the source is null, the result is null.
(deftest compile-median-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/median source)) {} nil nil))
      #elm/list [#elm/decimal"2" #elm/decimal"10" #elm/decimal"8"] 8M
      #elm/list [#elm/integer"2" #elm/integer"10" #elm/integer"8"] 8
      #elm/list [#elm/integer"1" #elm/integer"2"] 1.5M
      #elm/list [#elm/integer"1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.9. Min
;;
;; The Min operator returns the minimum element in the source. Comparison
;; semantics are defined by the comparison operators for the type of the values
;; being aggregated.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the source is null, the result is null.
(deftest compile-min-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/min source)) {} nil nil))
      #elm/list [#elm/decimal"2" #elm/decimal"8"] 2M
      #elm/list [#elm/integer"2" #elm/integer"8"] 2
      #elm/list [#elm/integer"1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.10. Mode
;;
;; The Mode operator returns the statistical mode of the elements in source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the source is null, the result is null.
(deftest compile-mode-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/mode source)) {} nil nil))
      #elm/list [#elm/decimal"2" #elm/decimal"2" #elm/decimal"8"] 2M
      #elm/list [#elm/integer"2" #elm/integer"2" #elm/integer"8"] 2
      #elm/list [#elm/integer"1"] 1
      #elm/list [#elm/integer"1" {:type "Null"} {:type "Null"}] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.11. PopulationVariance
;;
;; The PopulationVariance operator returns the statistical population variance
;; of the elements in source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the source is null, the result is null.
(deftest compile-population-variance-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/population-variance source)) {} nil nil))
      #elm/list [#elm/decimal"1" #elm/decimal"2" #elm/decimal"3" #elm/decimal"4" #elm/decimal"5"] 2M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.12. PopulationStdDev
;;
;; The PopulationStdDev operator returns the statistical standard deviation of
;; the elements in source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the source is null, the result is null.
(deftest compile-population-std-dev-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/population-std-dev source)) {} nil nil))
      #elm/list [#elm/decimal"1" #elm/decimal"2" #elm/decimal"3" #elm/decimal"4" #elm/decimal"5"] 1.41421356M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.13. Sum
;;
;; The Sum operator returns the sum of non-null elements in the source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the list is null, the result is null.
(deftest compile-sum-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/sum source)) {} nil nil))
      #elm/list [#elm/decimal"2" #elm/decimal"8"] 10M
      #elm/list [#elm/integer"2" #elm/integer"8"] 10
      #elm/list [#elm/integer"1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.14. StdDev
;;
;; The StdDev operator returns the statistical standard deviation of the
;; elements in source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the list is null, the result is null.
(deftest compile-std-dev-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/std-dev source)) {} nil nil))
      #elm/list [#elm/decimal"1" #elm/decimal"2" #elm/decimal"3" #elm/decimal"4" #elm/decimal"5"] 1.58113883M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))


;; 21.15. Variance
;;
;; The Variance operator returns the statistical variance of the elements in
;; source.
;;
;; If a path is specified, elements with no value for the property specified by
;; the path are ignored.
;;
;; If the source contains no non-null elements, null is returned.
;;
;; If the source is null, the result is null.
(deftest compile-variance-test
  (testing "Without path"
    (are [source res] (= res (core/-eval (c/compile {} (elm/variance source)) {} nil nil))
      #elm/list [#elm/decimal"1" #elm/decimal"2" #elm/decimal"3" #elm/decimal"4" #elm/decimal"5"] 2.5M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))
