(ns blaze.elm.compiler.list-operators-test
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.list-operators]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [blaze.elm.quantity :as quantity]
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


;; 20.1. List
;;
;; The List selector returns a value of type List, whose elements are the result
;; of evaluating the arguments to the List selector, in order.
;;
;; If a typeSpecifier element is provided, the list is of that type. Otherwise,
;; the static type of the first argument determines the type of the resulting
;; list, and each subsequent argument must be of that same type.
;;
;; If any argument is null, the resulting list will have null for that element.
(deftest compile-list-test
  (are [elm res] (= res (core/-eval (c/compile {} elm) {} nil nil))
    #elm/list []
    []

    #elm/list [{:type "Null"}]
    [nil]

    #elm/list [#elm/integer"1"]
    [1]

    #elm/list [#elm/integer"1" {:type "Null"}]
    [1 nil]

    #elm/list [#elm/integer"1" #elm/integer"2"]
    [1 2]))


;; 20.2. Contains
;;
;; See 19.5. Contains


;; 20.3. Current
;;
;; The Current expression returns the value of the object currently in scope.
;; For example, within a ForEach expression, this returns the current element
;; being considered in the iteration.
;;
;; It is an error to invoke the Current operator outside the context of a scoped
;; operation.
(deftest compile-current-test
  (are [x] (= x (core/-eval (c/compile {} {:type "Current"}) {} nil x))
    1)

  (are [x] (= x (core/-eval (c/compile {} {:type "Current" :scope "A"}) {} nil {"A" x}))
    1))


;; 20.4. Distinct
;;
;; The Distinct operator takes a list of elements and returns a list containing
;; only the unique elements within the input. For example, given the list of
;; integers { 1, 1, 1, 2, 2, 3, 4, 4 }, the result of Distinct would be
;; { 1, 2, 3, 4 }.
;;
;; The operator uses equality comparison semantics as defined in the Equal
;; operator. Because nulls compare unknown, this means that multiple nulls in
;; the input list will be preserved in the output.
;;
;; If the source argument is null, the result is null.
(deftest compile-distinct-test
  (are [list res] (= res (core/-eval (c/compile {} (elm/distinct list)) {} nil nil))
    #elm/list [#elm/integer"1"] [1]
    #elm/list [#elm/integer"1" #elm/integer"1"] [1]
    #elm/list [#elm/integer"1" #elm/integer"1" #elm/integer"2"] [1 2]
    #elm/list [{:type "Null"}] [nil]
    #elm/list [{:type "Null"} {:type "Null"}] [nil nil]
    #elm/list [{:type "Null"} {:type "Null"} {:type "Null"}] [nil nil nil]
    #elm/list [#elm/quantity[100 "cm"] #elm/quantity[1 "m"]] [(quantity/quantity 100 "cm")]
    #elm/list [#elm/quantity[1 "m"] #elm/quantity[100 "cm"]] [(quantity/quantity 1 "m")]

    {:type "Null"} nil))


;; 20.5. Equal
;;
;; See 12.1. Equal


;; 20.6. Equivalent
;;
;; 12.2. Equivalent


;; 20.7. Except
;;
;; 19.10. Except


;; 20.8. Exists
;;
;; The Exists operator returns true if the list contains any elements.
;;
;; If the argument is null, the result is false.
(deftest compile-exists-test
  (are [list res] (= res (core/-eval (c/compile {} (elm/exists list)) {} nil nil))
    #elm/list [#elm/integer"1"] true
    #elm/list [#elm/integer"1" #elm/integer"1"] true
    #elm/list [] false

    {:type "Null"} false))


;; 20.9. Filter
;;
;; The Filter operator returns a list with only those elements in the source
;; list for which the condition element evaluates to true.
;;
;; If the source argument is null, the result is null.
(deftest compile-filter-test
  (are [source condition res] (= res (core/-eval (c/compile {} {:type "Filter" :source source :condition condition :scope "A"}) {} nil nil))
    #elm/list [#elm/integer"1"] #elm/boolean"false" []
    #elm/list [#elm/integer"1"] #elm/equal [#elm/current "A" #elm/integer"1"] [1]

    {:type "Null"} #elm/boolean"true" nil))


;; 20.10. First
;;
;; The First operator returns the first element in a list. If the order by
;; attribute is specified, the list is sorted by that ordering prior to
;; returning the first element.
;;
;; If the argument is null, the result is null.
(deftest compile-first-test
  (are [source res] (= res (core/-eval (c/compile {} {:type "First" :source source}) {} nil nil))
    #elm/list [#elm/integer"1"] 1
    #elm/list [#elm/integer"1" #elm/integer"2"] 1

    {:type "Null"} nil))


;; 20.11. Flatten
;;
;; The Flatten operator flattens a list of lists into a single list.
;;
;; If the argument is null, the result is null.
(deftest compile-flatten-test
  (are [list res] (= res (core/-eval (c/compile {} (elm/flatten list)) {} nil nil))
    #elm/list [] []
    #elm/list [#elm/integer"1"] [1]
    #elm/list [#elm/integer"1" #elm/list [#elm/integer"2"]] [1 2]
    #elm/list [#elm/integer"1" #elm/list [#elm/integer"2"] #elm/integer"3"] [1 2 3]
    #elm/list [#elm/integer"1" #elm/list [#elm/integer"2" #elm/list [#elm/integer"3"]]] [1 2 3]
    #elm/list [#elm/list [#elm/integer"1" #elm/list [#elm/integer"2"]] #elm/integer"3"] [1 2 3]

    {:type "Null"} nil))


;; 20.12. ForEach
;;
;; The ForEach expression iterates over the list of elements in the source
;; element, and returns a list with the same number of elements, where each
;; element in the new list is the result of evaluating the element expression
;; for each element in the source list.
;;
;; If the source argument is null, the result is null.
;;
;; If the element argument evaluates to null for some item in the source list,
;; the resulting list will contain a null for that element.
(deftest compile-for-each-test
  (testing "Without scope"
    (are [source element res] (= res (core/-eval (c/compile {} {:type "ForEach" :source source :element element}) {} nil nil))
      #elm/list [#elm/integer"1"] {:type "Null"} [nil]

      {:type "Null"} {:type "Null"} nil))

  (testing "With scope"
    (are [source element res] (= res (core/-eval (c/compile {} {:type "ForEach" :source source :element element :scope "A"}) {} nil nil))
      #elm/list [#elm/integer"1"] #elm/current "A" [1]
      #elm/list [#elm/integer"1" #elm/integer"2"] #elm/add [#elm/current "A" #elm/integer"1"] [2 3]

      {:type "Null"} {:type "Null"} nil)))


;; 20.13. In
;;
;; See 19.12. In


;; 20.14. Includes
;;
;; See 19.13. Includes


;; 20.15. IncludedIn
;;
;; See 19.14. IncludedIn


;; 20.16. IndexOf
;;
;; The IndexOf operator returns the 0-based index of the given element in the
;; given source list.
;;
;; The operator uses equality semantics as defined in the Equal operator to
;; determine the index. The search is linear, and returns the index of the first
;; element for which the equality comparison returns true.
;;
;; If the list is empty, or no element is found, the result is -1.
;;
;; If either argument is null, the result is null.
(deftest compile-index-of-test
  (are [source element res] (= res (core/-eval (c/compile {} {:type "IndexOf" :source source :element element}) {} nil nil))
    #elm/list [] #elm/integer"1" -1
    #elm/list [#elm/integer"1"] #elm/integer"1" 0
    #elm/list [#elm/integer"1" #elm/integer"1"] #elm/integer"1" 0
    #elm/list [#elm/integer"1" #elm/integer"2"] #elm/integer"2" 1

    #elm/list [] {:type "Null"} nil
    {:type "Null"} #elm/integer"1" nil
    {:type "Null"} {:type "Null"} nil))


;; 20.17. Intersect
;;
;; See 19.15. Intersect


;; 20.18. Last
;;
;; The Last operator returns the last element in a list. If the order by
;; attribute is specified, the list is sorted by that ordering prior to
;; returning the last element.
;;
;; If the argument is null, the result is null.
(deftest compile-last-test
  (are [source res] (= res (core/-eval (c/compile {} {:type "Last" :source source}) {} nil nil))
    #elm/list [#elm/integer"1"] 1
    #elm/list [#elm/integer"1" #elm/integer"2"] 2

    {:type "Null"} nil))


;; 20.19. Not Equal
;;
;; See 12.7. NotEqual


;; 20.20. ProperContains
;;
;; See 19.24. ProperContains


;; 20.21. ProperIn
;;
;; See 19.25. ProperIn


;; 20.22. ProperIncludes
;;
;; See 19.26. ProperIncludes


;; 20.23. ProperIncludedIn
;;
;; See 19.27. ProperIncludedIn


;; 20.24. Repeat
;;
;; The Repeat expression performs successive ForEach until no new elements are
;; returned.
;;
;; The operator uses equality comparison semantics as defined in the Equal
;; operator.
;;
;; If the source argument is null, the result is null.
;;
;; If the element argument evaluates to null for some item in the source list,
;; the resulting list will contain a null for that element.
;;
;; TODO: not implemented


;; 20.25. SingletonFrom
;;
;; The SingletonFrom expression extracts a single element from the source list.
;; If the source list is empty, the result is null. If the source list contains
;; one element, that element is returned. If the list contains more than one
;; element, a run-time error is thrown. If the source list is null, the result
;; is null.
(deftest compile-singleton-from-test
  (are [list res] (= res (core/-eval (c/compile {} (elm/singleton-from list)) {} nil nil))
    #elm/list [] nil
    #elm/list [#elm/integer"1"] 1
    {:type "Null"} nil)

  (are [list] (thrown? Exception (core/-eval (c/compile {} (elm/singleton-from list)) {} nil nil))
    #elm/list [#elm/integer"1" #elm/integer"1"]))


;; 20.26. Slice
;;
;; The Slice operator returns a portion of the elements in a list, beginning at
;; the start index and ending just before the ending index.
;;
;; If the source list is null, the result is null.
;;
;; If the startIndex is null, the slice begins at the first element of the list.
;;
;; If the endIndex is null, the slice continues to the last element of the list.
;;
;; If the startIndex or endIndex is less than 0, or if the endIndex is less than
;; the startIndex, the result is an empty list.
(deftest compile-slice-test
  (are [source start end res] (= res (core/-eval (c/compile {} {:type "Slice" :source source :startIndex start :endIndex end}) {} nil nil))
    #elm/list [#elm/integer"1"] #elm/integer"0" #elm/integer"1" [1]
    #elm/list [#elm/integer"1" #elm/integer"2"] #elm/integer"0" #elm/integer"1" [1]
    #elm/list [#elm/integer"1" #elm/integer"2"] #elm/integer"1" #elm/integer"2" [2]
    #elm/list [#elm/integer"1" #elm/integer"2" #elm/integer"3"] #elm/integer"1" #elm/integer"3" [2 3]
    #elm/list [#elm/integer"1" #elm/integer"2"] {:type "Null"} {:type "Null"} [1 2]

    #elm/list [#elm/integer"1"] #elm/integer"-1" #elm/integer"0" []
    #elm/list [#elm/integer"1"] #elm/integer"1" #elm/integer"0" []


    {:type "Null"} #elm/integer"0" #elm/integer"0" nil
    {:type "Null"} {:type "Null"} {:type "Null"} nil))


;; 20.27. Sort
;;
;; The Sort operator returns a list with all the elements in source, sorted as
;; described by the by element.
;;
;; When the sort elements do not provide a unique ordering (i.e. there is a
;; possibility of duplicate sort values in the result), the order of duplicates
;; is unspecified.
;;
;; If the argument is null, the result is null.
(deftest compile-sort-test
  (are [source by res] (= res (core/-eval (c/compile {} {:type "Sort" :source source :by [by]}) {} nil nil))
    #elm/list [#elm/integer"2" #elm/integer"1"]
    {:type "ByDirection" :direction "asc"} [1 2]

    #elm/list [#elm/integer"1" #elm/integer"2"]
    {:type "ByDirection" :direction "desc"} [2 1]

    {:type "Null"} {:type "ByDirection" :direction "asc"} nil))


;; 20.28. Times
;;
;; The Times operator performs the cartesian product of two lists of tuples.
;; The return type of a Times operator is a tuple with all the components from
;; the tuple types of both arguments. The result will contain a tuple for each
;; possible combination of tuples from both arguments with the values for each
;; component derived from the pairing of the source tuples.
;;
;; If either argument is null, the result is null.
;;
;; TODO: not implemented


;; 20.29. Union
;;
;; See 19.31. Union
