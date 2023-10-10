(ns blaze.elm.compiler.aggregate-operators-test
  "21. Aggregate Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.aggregate-operators]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.core-spec]
    [blaze.elm.compiler.test-util :as ctu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(st/instrument)
(ctu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
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
      #elm/list [#elm/boolean "true" #elm/boolean "false"] false
      #elm/list [#elm/boolean "false"] false
      #elm/list [#elm/boolean "true"] true

      #elm/list [{:type "Null"}] true
      #elm/list [] true
      {:type "Null"} true))

  (ctu/testing-unary-dynamic elm/all-true)

  (ctu/testing-unary-attach-cache elm/all-true)

  (ctu/testing-unary-resolve-expr-ref elm/all-true)

  (ctu/testing-unary-resolve-param elm/all-true)

  (ctu/testing-unary-form elm/all-true))


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
      #elm/list [#elm/boolean "true" #elm/boolean "false"] true
      #elm/list [#elm/boolean "false"] false
      #elm/list [#elm/boolean "true"] true

      #elm/list [{:type "Null"}] false
      #elm/list [] false
      {:type "Null"} false))

  (ctu/testing-unary-dynamic elm/any-true)

  (ctu/testing-unary-attach-cache elm/any-true)

  (ctu/testing-unary-resolve-expr-ref elm/any-true)

  (ctu/testing-unary-resolve-param elm/any-true)

  (ctu/testing-unary-form elm/any-true))


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
      #elm/list [#elm/decimal "1" #elm/decimal "2"] 1.5M
      #elm/list [#elm/integer "1" #elm/integer "2"] 1.5M
      #elm/list [#elm/integer "1"] 1M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/avg)

  (ctu/testing-unary-attach-cache elm/avg)

  (ctu/testing-unary-resolve-expr-ref elm/avg)

  (ctu/testing-unary-resolve-param elm/avg)

  (ctu/testing-unary-form elm/avg))


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
    (are [source res] (identical? res (core/-eval (c/compile {} (elm/count source)) {} nil nil))
      #elm/list [#elm/integer "1"] 1
      #elm/list [#elm/integer "1" {:type "Null"}] 1
      #elm/list [#elm/integer "1" #elm/integer "1"] 2
      #elm/list [#elm/integer "1" {:type "Null"} #elm/integer "1"] 2
      #elm/list [#elm/integer "1" #elm/integer "1" {:type "Null"}] 2

      #elm/list [{:type "Null"}] 0
      #elm/list [] 0
      {:type "Null"} 0))

  (ctu/testing-unary-dynamic elm/count)

  (ctu/testing-unary-attach-cache elm/count)

  (ctu/testing-unary-resolve-expr-ref elm/count)

  (ctu/testing-unary-resolve-param elm/count)

  (ctu/testing-unary-form elm/count))


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
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 4M
      #elm/list [#elm/integer "2" #elm/integer "8"] 4M
      #elm/list [#elm/integer "1"] 1M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/geometric-mean)

  (ctu/testing-unary-attach-cache elm/geometric-mean)

  (ctu/testing-unary-resolve-expr-ref elm/geometric-mean)

  (ctu/testing-unary-resolve-param elm/geometric-mean)

  (ctu/testing-unary-form elm/geometric-mean))


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
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 16M
      #elm/list [#elm/integer "2" #elm/integer "8"] 16
      #elm/list [#elm/integer "1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/product)

  (ctu/testing-unary-attach-cache elm/product)

  (ctu/testing-unary-resolve-expr-ref elm/product)

  (ctu/testing-unary-resolve-param elm/product)

  (ctu/testing-unary-form elm/product))


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
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 8M
      #elm/list [#elm/integer "2" #elm/integer "8"] 8
      #elm/list [#elm/integer "1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/max)

  (ctu/testing-unary-attach-cache elm/max)

  (ctu/testing-unary-resolve-expr-ref elm/max)

  (ctu/testing-unary-resolve-param elm/max)

  (ctu/testing-unary-form elm/max))


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
      #elm/list [#elm/decimal "2" #elm/decimal "10" #elm/decimal "8"] 8M
      #elm/list [#elm/integer "2" #elm/integer "10" #elm/integer "8"] 8
      #elm/list [#elm/integer "1" #elm/integer "2"] 1.5M
      #elm/list [#elm/integer "1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/median)

  (ctu/testing-unary-attach-cache elm/median)

  (ctu/testing-unary-resolve-expr-ref elm/median)

  (ctu/testing-unary-resolve-param elm/median)

  (ctu/testing-unary-form elm/median))


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
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 2M
      #elm/list [#elm/integer "2" #elm/integer "8"] 2
      #elm/list [#elm/integer "1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/min)

  (ctu/testing-unary-attach-cache elm/min)

  (ctu/testing-unary-resolve-expr-ref elm/min)

  (ctu/testing-unary-resolve-param elm/min)

  (ctu/testing-unary-form elm/min))


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
      #elm/list [#elm/decimal "2" #elm/decimal "2" #elm/decimal "8"] 2M
      #elm/list [#elm/integer "2" #elm/integer "2" #elm/integer "8"] 2
      #elm/list [#elm/integer "1"] 1
      #elm/list [#elm/integer "1" {:type "Null"} {:type "Null"}] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/mode)

  (ctu/testing-unary-attach-cache elm/mode)

  (ctu/testing-unary-resolve-expr-ref elm/mode)

  (ctu/testing-unary-resolve-param elm/mode)

  (ctu/testing-unary-form elm/mode))


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
      #elm/list [#elm/decimal "1" #elm/decimal "2" #elm/decimal "3" #elm/decimal "4" #elm/decimal "5"] 2M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/population-variance)

  (ctu/testing-unary-attach-cache elm/population-variance)

  (ctu/testing-unary-resolve-expr-ref elm/population-variance)

  (ctu/testing-unary-resolve-param elm/population-variance)

  (ctu/testing-unary-form elm/population-variance))


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
      #elm/list [#elm/decimal "1" #elm/decimal "2" #elm/decimal "3" #elm/decimal "4" #elm/decimal "5"] 1.41421356M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/population-std-dev)

  (ctu/testing-unary-attach-cache elm/population-std-dev)

  (ctu/testing-unary-resolve-expr-ref elm/population-std-dev)

  (ctu/testing-unary-resolve-param elm/population-std-dev)

  (ctu/testing-unary-form elm/population-std-dev))


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
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 10M
      #elm/list [#elm/integer "2" #elm/integer "8"] 10
      #elm/list [#elm/integer "1"] 1

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/sum)

  (ctu/testing-unary-attach-cache elm/sum)

  (ctu/testing-unary-resolve-expr-ref elm/sum)

  (ctu/testing-unary-resolve-param elm/sum)

  (ctu/testing-unary-form elm/sum))


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
      #elm/list [#elm/decimal "1" #elm/decimal "2" #elm/decimal "3" #elm/decimal "4" #elm/decimal "5"] 1.58113883M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/std-dev)

  (ctu/testing-unary-attach-cache elm/std-dev)

  (ctu/testing-unary-resolve-expr-ref elm/std-dev)

  (ctu/testing-unary-resolve-param elm/std-dev)

  (ctu/testing-unary-form elm/std-dev))


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
      #elm/list [#elm/decimal "1" #elm/decimal "2" #elm/decimal "3" #elm/decimal "4" #elm/decimal "5"] 2.5M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil))

  (ctu/testing-unary-dynamic elm/variance)

  (ctu/testing-unary-attach-cache elm/variance)

  (ctu/testing-unary-resolve-expr-ref elm/variance)

  (ctu/testing-unary-resolve-param elm/variance)

  (ctu/testing-unary-form elm/variance))
