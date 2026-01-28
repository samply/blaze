(ns blaze.elm.compiler.list-operators-test
  "20. List Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
   [blaze.anomaly-spec]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler-spec]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.list-operators]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.expression :as expr]
   [blaze.elm.expression-spec]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache-spec]
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [blaze.elm.quantity :refer [quantity]]
   [blaze.elm.resource :as cr]
   [blaze.elm.spec :as elm-spec]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :refer [given-thrown satisfies-prop]]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.properties :as prop]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
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
  (testing "Static"
    (are [elm res] (= res (c/compile {} elm))
      #elm/list [] []
      #elm/list [{:type "Null"}] [nil]
      #elm/list [#elm/integer "1"] [1]
      #elm/list [#elm/integer "1" {:type "Null"}] [1 nil]
      #elm/list [#elm/integer "1" #elm/integer "2"] [1 2])

    (testing "form"
      (are [elm res] (= res (c/form (c/compile {} elm)))
        #elm/list [] []
        #elm/list [#elm/date "2023-11-02"] [#system/date "2023-11-02"]
        #elm/list [#elm/date-time "2023-11-02T14:23"] [#system/date-time "2023-11-02T14:23"])))

  (testing "Dynamic"
    (are [elm res] (= res (ctu/dynamic-compile-eval elm))
      #elm/list [#elm/parameter-ref "nil"] [nil]
      #elm/list [#elm/parameter-ref "1"] [1]
      #elm/list [#elm/parameter-ref "1" #elm/parameter-ref "nil"] [1 nil]
      #elm/list [#elm/parameter-ref "1" #elm/parameter-ref "2"] [1 2])

    (let [expr (ctu/dynamic-compile #elm/list [#elm/parameter-ref "x"])]

      (testing "expression is dynamic"
        (is (false? (core/-static expr))))

      (ctu/testing-constant-patient-count expr)

      (testing "resolve parameters"
        (has-form (c/resolve-params expr {"x" 1}) '(list 1)))

      (testing "form"
        (has-form expr '(list (param-ref "x"))))))

  (testing "attach cache"
    (with-redefs [ec/get #(do (assert (= ::cache %1)) (c/form %2))]
      (testing "with one element"
        (let [elm (elm/list [#elm/exists #elm/retrieve{:type "Observation"}])
              ctx {:eval-context "Patient"}
              expr (c/compile ctx elm)]
          (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
            count := 2
            [0] := expr
            [1 count] := 1
            [1 0] := '(exists (retrieve "Observation")))))

      (testing "with two elements"
        (let [elm (elm/list [#elm/exists #elm/retrieve{:type "Observation"}
                             #elm/exists #elm/retrieve{:type "Condition"}])
              ctx {:eval-context "Patient"}
              expr (c/compile ctx elm)]
          (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
            count := 2
            [0] := expr
            [1 count] := 2
            [1 0] := '(exists (retrieve "Observation"))
            [1 1] := '(exists (retrieve "Condition")))))))

  (testing "resolve expression references"
    (let [elm #elm/list [#elm/expression-ref "x"]
          expr-def {:type "ExpressionDef" :name "x" :expression 1
                    :context "Patient"}
          ctx {:library {:statements {:def [expr-def]}}}
          expr (c/resolve-refs (c/compile ctx elm) {"x" expr-def})]
      (has-form expr '(list 1))))

  (testing "optimize"
    (let [elm #elm/list [#ctu/optimizeable "x"]
          expr (st/with-instrument-disabled (c/optimize (c/compile {} elm) nil))]
      (has-form expr '(list (optimized "x")))))

  (ctu/testing-equals-hash-code #elm/list [#elm/parameter-ref "1"]))

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
  (testing "default scope"
    (satisfies-prop 100
      (prop/for-all [x (s/gen int?)]
        (= x (core/-eval (c/compile {} #elm/current nil) {} nil x))))

    (let [expr (c/compile {} #elm/current nil)]

      (testing "expression is dynamic"
        (is (false? (core/-static expr))))

      (ctu/testing-constant-patient-count expr)

      (testing "resolve expression references"
        (has-form (c/resolve-refs expr {}) 'current))

      (testing "resolve parameters"
        (has-form (c/resolve-params expr {}) 'current))

      (testing "optimize"
        (has-form (st/with-instrument-disabled (c/optimize expr nil)) 'current))

      (testing "form"
        (has-form expr 'current)))

    (ctu/testing-equals-hash-code #elm/current nil))

  (testing "named scope"
    (satisfies-prop 100
      (prop/for-all [scope (s/gen string?)
                     x (s/gen int?)]
        (let [expr (c/compile {} (elm/current scope))]
          (= x (core/-eval expr {} nil {scope x})))))

    (let [expr (c/compile {} #elm/current "x")]

      (testing "expression is dynamic"
        (is (false? (core/-static expr))))

      (ctu/testing-constant-patient-count expr)

      (testing "resolve expression references"
        (has-form (c/resolve-refs expr {}) '(current "x")))

      (testing "resolve parameters"
        (has-form (c/resolve-params expr {}) '(current "x")))

      (testing "optimize"
        (has-form (st/with-instrument-disabled (c/optimize expr nil)) '(current "x")))

      (testing "form"
        (has-form expr '(current "x"))))

    (ctu/testing-equals-hash-code #elm/current "x")))

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
  (are [list res] (= res (c/compile {} (elm/distinct list)))
    #elm/list [#elm/integer "1"] [1]
    #elm/list [#elm/integer "1" #elm/integer "1"] [1]
    #elm/list [#elm/integer "1" #elm/integer "1" #elm/integer "2"] [1 2]
    #elm/list [{:type "Null"}] [nil]
    #elm/list [{:type "Null"} {:type "Null"}] [nil nil]
    #elm/list [{:type "Null"} {:type "Null"} {:type "Null"}] [nil nil nil]
    #elm/list [#elm/quantity [100 "cm"] #elm/quantity [1 "m"]] [(quantity 100 "cm")]
    #elm/list [#elm/quantity [1 "m"] #elm/quantity [100 "cm"]] [(quantity 1 "m")])

  (ctu/testing-unary-null elm/distinct)

  (ctu/testing-unary-op elm/distinct)

  (ctu/testing-optimize elm/distinct
    (testing "[1 1]"
      #ctu/optimize-to [1 1]
      [1])))

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
;; The Exists operator returns true if the list contains any non-null elements.
;;
;; If the argument is null, the result is false.
(def ^:private exists-config
  (assoc
   api-stub/mem-node-config
   ::expr/cache
   {:node (ig/ref :blaze.db/node)
    :executor (ig/ref :blaze.test/executor)}
   :blaze.test/executor {}))

(defmethod elm-spec/expression :elm.spec.type/exists-test [_]
  map?)

(defmethod core/compile* :elm.compiler.type/exists-test
  [_ _]
  (reify-expr core/Expression
    (-attach-cache [_ _]
      [(reify-expr core/Expression
         (-form [_]
           'exists-test-with-cache))])
    (-form [_]
      'exists-test)))

(deftest compile-exists-test
  (testing "Static"
    (are [list res] (= res (c/compile {} (elm/exists list)))
      #elm/list [#elm/integer "1"] true
      #elm/list [{:type "Null"} #elm/integer "1"] true
      #elm/list [#elm/integer "1" #elm/integer "1"] true
      #elm/list [{:type "Null"}] false
      #elm/list [] false

      {:type "Null"} false))

  (testing "Dynamic"
    (are [list res] (= res (ctu/dynamic-compile-eval (elm/exists list)))
      #elm/list [#elm/parameter-ref "1"] true
      #elm/list [#elm/parameter-ref "nil" #elm/parameter-ref "1"] true
      #elm/list [#elm/parameter-ref "1" #elm/parameter-ref "1"] true
      #elm/list [#elm/parameter-ref "nil"] false
      #elm/list [] false

      {:type "Null"} false)

    (testing "doesn't attach the cache downstream because there is no need for double caching"
      (with-system [{::expr/keys [cache]} exists-config]
        (let [elm #elm/exists {:type "ExistsTest"}
              expr (c/compile {:eval-context "Patient"} elm)
              ;; ensure Bloom filter is available
              _ (do (c/attach-cache expr cache) (Thread/sleep 100))
              expr (first (c/attach-cache expr cache))]

          (has-form expr '(exists exists-test))

          (testing "also not at a second cache attachment"
            (has-form (first (c/attach-cache expr cache)) '(exists exists-test))))))

    (testing "with caching expressions"
      (with-system-data [{:blaze.db/keys [node] ::expr/keys [cache]} exists-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [db (d/db node)
              patient (cr/mk-resource db (d/resource-handle db "Patient" "0"))
              elm #elm/exists #elm/retrieve{:type "Observation"}
              compile-context
              {:node node
               :eval-context "Patient"
               :library {}}
              expr (c/compile compile-context elm)
              eval-context
              {:db db
               :now (time/offset-date-time)}]

          (testing "has no Observation at the beginning"
            (let [[expr bloom-filters] (c/attach-cache expr cache)]
              (is (every? ba/unavailable? bloom-filters))
              (is (false? (expr/eval eval-context expr patient)))))

          (Thread/sleep 100)

          (testing "has still no Observation after the Bloom filter is filled"
            (let [[expr bloom-filters] (c/attach-cache expr cache)]
              (given bloom-filters
                count := 1
                [0 ::bloom-filter/expr-form] := (pr-str '(exists (retrieve "Observation")))
                [0 ::bloom-filter/patient-count] := 0)
              (is (false? (expr/eval eval-context expr patient)))))

          (let [tx-op [:put {:fhir/type :fhir/Observation :id "0"
                             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
                db-after @(d/transact node [tx-op])]

            (testing "has an Observation after transaction"
              (let [[expr bloom-filters] (c/attach-cache expr cache)
                    patient (cr/mk-resource db-after (d/resource-handle db "Patient" "0"))]
                (testing "even so the bloom filter is still the old one"
                  (given bloom-filters
                    count := 1
                    [0 ::bloom-filter/expr-form] := (pr-str '(exists (retrieve "Observation")))
                    [0 ::bloom-filter/patient-count] := 0))
                (is (true? (expr/eval (assoc eval-context :db db-after) expr patient)))))

            (testing "has still no Observation at the old database"
              (let [[expr] (c/attach-cache expr cache)]
                (is (false? (expr/eval eval-context expr patient)))))))))

    (testing "without caching expressions"
      (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [db (d/db node)
              patient (cr/mk-resource db (d/resource-handle db "Patient" "0"))
              elm #elm/exists #elm/retrieve{:type "Observation"}
              compile-context
              {:node node
               :eval-context "Patient"
               :library {}}
              expr (c/compile compile-context elm)
              eval-context
              {:db db
               :now (time/offset-date-time)}]

          (testing "has no Observation at the beginning"
            (is (false? (expr/eval eval-context expr patient))))

          (let [tx-op [:put {:fhir/type :fhir/Observation :id "0"
                             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
                db-after @(d/transact node [tx-op])]

            (testing "has an Observation after transaction"
              (let [patient (cr/mk-resource db-after (d/resource-handle db "Patient" "0"))]
                (is (true? (expr/eval (assoc eval-context :db db-after) expr patient)))))

            (testing "has still no Observation at the old database"
              (is (false? (expr/eval eval-context expr patient)))))))))

  (ctu/testing-unary-dynamic elm/exists)

  (ctu/testing-unary-patient-count elm/exists)

  (ctu/testing-unary-resolve-refs elm/exists)

  (ctu/testing-unary-resolve-params elm/exists)

  (ctu/testing-unary-optimize elm/exists)

  (testing "optimize to false if operand optimizes to an empty list"
    (let [elm (elm/exists #ctu/optimize-to [])
          expr (c/compile {:eval-context "Patient"} elm)
          expr (st/with-instrument-disabled (c/optimize expr nil))]
      (has-form expr false)))

  (ctu/testing-optimize elm/exists
    (testing "empty list"
      #ctu/optimize-to []
      false))

  (ctu/testing-unary-equals-hash-code elm/exists)

  (ctu/testing-unary-form elm/exists))

;; 20.9. Filter
;;
;; The Filter operator returns a list with only those elements in the source
;; list for which the condition element evaluates to true.
;;
;; If the source argument is null, the result is null.
(deftest compile-filter-test
  (testing "eval"
    (let [eval #(core/-eval % {} nil nil)]
      (testing "with scope"
        (are [source condition res] (= res (eval (c/compile {} {:type "Filter"
                                                                :source source
                                                                :condition condition
                                                                :scope "A"})))
          #elm/list [#elm/integer "1"] #elm/boolean "false" []
          #elm/list [#elm/integer "1"] #elm/equal [#elm/current "A" #elm/integer "1"] [1]

          {:type "Null"} #elm/boolean "true" nil))

      (testing "without scope"
        (are [source condition res] (= res (eval (c/compile {} {:type "Filter"
                                                                :source source
                                                                :condition condition})))
          #elm/list [#elm/integer "1"] #elm/boolean "false" []
          #elm/list [#elm/integer "1"] #elm/equal [#elm/current nil #elm/integer "1"] [1]

          {:type "Null"} #elm/boolean "true" nil))))

  (testing "with scope"
    (let [expr (ctu/dynamic-compile {:type "Filter"
                                     :source #elm/parameter-ref "x"
                                     :condition #elm/parameter-ref "y"
                                     :scope "A"})]

      (testing "expression is dynamic"
        (is (false? (core/-static expr))))

      (ctu/testing-constant-attach-cache expr)

      (ctu/testing-constant-patient-count expr)

      (testing "resolve expression references"
        (let [elm {:type "Filter"
                   :source #elm/expression-ref "x"
                   :condition #elm/expression-ref "y"
                   :scope "A"}
              source-def {:type "ExpressionDef" :name "x" :expression [1]
                          :context "Patient"}
              condition-def {:type "ExpressionDef" :name "y" :expression true
                             :context "Patient"}
              ctx {:library {:statements {:def [source-def condition-def]}}}
              expr (c/resolve-refs (c/compile ctx elm) {"x" source-def "y" condition-def})]
          (has-form expr '(filter [1] true "A"))))

      (testing "resolve parameters"
        (has-form (c/resolve-params expr {"x" [1]})
          '(filter [1] (param-ref "y") "A")))

      (testing "optimize"
        (let [elm {:type "Filter"
                   :source #ctu/optimizeable "x"
                   :condition #ctu/optimizeable "y"
                   :scope "A"}
              expr (st/with-instrument-disabled (c/optimize (c/compile {} elm) nil))]
          (has-form expr '(filter (optimized "x") (optimized "y") "A"))))

      (testing "form"
        (has-form expr '(filter (param-ref "x") (param-ref "y") "A")))

      (ctu/testing-equals-hash-code {:type "Filter"
                                     :source #elm/parameter-ref "x"
                                     :condition #elm/parameter-ref "y"
                                     :scope "A"})))

  (testing "without scope"
    (let [expr (ctu/dynamic-compile {:type "Filter"
                                     :source #elm/parameter-ref "x"
                                     :condition #elm/parameter-ref "y"})]

      (testing "expression is dynamic"
        (is (false? (core/-static expr))))

      (ctu/testing-constant-attach-cache expr)

      (ctu/testing-constant-patient-count expr)

      (testing "resolve expression references"
        (let [elm {:type "Filter"
                   :source #elm/expression-ref "x"
                   :condition #elm/expression-ref "y"}
              source-def {:type "ExpressionDef" :name "x" :expression [1]
                          :context "Patient"}
              condition-def {:type "ExpressionDef" :name "y" :expression true
                             :context "Patient"}
              ctx {:library {:statements {:def [source-def condition-def]}}}
              expr (c/resolve-refs (c/compile ctx elm) {"x" source-def "y" condition-def})]
          (has-form expr '(filter [1] true))))

      (testing "resolve parameters"
        (has-form (c/resolve-params expr {"x" [1]})
          '(filter [1] (param-ref "y"))))

      (testing "optimize"
        (let [elm {:type "Filter"
                   :source #ctu/optimizeable "x"
                   :condition #ctu/optimizeable "y"}
              expr (st/with-instrument-disabled (c/optimize (c/compile {} elm) nil))]
          (has-form expr '(filter (optimized "x") (optimized "y")))))

      (testing "form"
        (has-form expr '(filter (param-ref "x") (param-ref "y"))))

      (ctu/testing-equals-hash-code {:type "Filter"
                                     :source #elm/parameter-ref "x"
                                     :condition #elm/parameter-ref "y"}))))

;; 20.10. First
;;
;; The First operator returns the first element in a list. If the order by
;; attribute is specified, the list is sorted by that ordering prior to
;; returning the first element.
;;
;; If the argument is null, the result is null.
(deftest compile-first-test
  (testing "Static"
    (are [source res] (= res (core/-eval (c/compile {} (elm/first source)) {} nil nil))
      #elm/list [#elm/integer "1"] 1
      #elm/list [#elm/integer "1" #elm/integer "2"] 1))

  (testing "Dynamic"
    (are [source res] (= res (ctu/dynamic-compile-eval (elm/first source)))
      #elm/parameter-ref "[1]" 1
      #elm/parameter-ref "[1 2]" 1))

  (ctu/testing-unary-null elm/first)

  (ctu/testing-unary-op elm/first)

  (ctu/testing-optimize elm/first
    (testing "[1 2]"
      #ctu/optimize-to [1 2]
      1)))

;; 20.11. Flatten
;;
;; The Flatten operator flattens a list of lists into a single list.
;;
;; If the argument is null, the result is null.
(deftest compile-flatten-test
  (are [list res] (= res (c/compile {} (elm/flatten list)))
    #elm/list [] []
    #elm/list [#elm/integer "1"] [1]
    #elm/list [#elm/integer "1" #elm/list [#elm/integer "2"]] [1 2]
    #elm/list [#elm/integer "1" #elm/list [#elm/integer "2"] #elm/integer "3"] [1 2 3]
    #elm/list [#elm/integer "1" #elm/list [#elm/integer "2" #elm/list [#elm/integer "3"]]] [1 2 3]
    #elm/list [#elm/list [#elm/integer "1" #elm/list [#elm/integer "2"]] #elm/integer "3"] [1 2 3])

  (ctu/testing-unary-null elm/flatten)

  (ctu/testing-unary-op elm/flatten)

  (ctu/testing-optimize elm/flatten
    (testing "[1 [2]]"
      #ctu/optimize-to [1 [2]]
      [1 2])))

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
  (testing "eval"
    (let [eval #(core/-eval % {} nil nil)]
      (testing "with scope"
        (are [source element res] (= res (eval (c/compile {} {:type "ForEach"
                                                              :source source
                                                              :element element
                                                              :scope "A"})))
          #elm/list [#elm/integer "1"] #elm/current "A" [1]
          #elm/list [#elm/integer "1" #elm/integer "2"] #elm/add [#elm/current "A" #elm/integer "1"] [2 3]

          {:type "Null"} {:type "Null"} nil))

      (testing "without scope"
        (are [source element res] (= res (eval (c/compile {} {:type "ForEach"
                                                              :source source
                                                              :element element})))
          #elm/list [#elm/integer "1"] #elm/current nil [1]
          #elm/list [#elm/integer "1" #elm/integer "2"] #elm/add [#elm/current nil #elm/integer "1"] [2 3]

          {:type "Null"} {:type "Null"} nil))))

  (testing "with scope"
    (let [expr (ctu/dynamic-compile {:type "ForEach"
                                     :source #elm/parameter-ref "x"
                                     :element #elm/parameter-ref "y"
                                     :scope "A"})]

      (testing "expression is dynamic"
        (is (false? (core/-static expr))))

      (ctu/testing-constant-attach-cache expr)

      (ctu/testing-constant-patient-count expr)

      (testing "resolve expression references"
        (let [elm {:type "ForEach"
                   :source #elm/expression-ref "x"
                   :element #elm/current "A"
                   :scope "A"}
              expr-def {:type "ExpressionDef" :name "x" :expression [1]
                        :context "Patient"}
              ctx {:library {:statements {:def [expr-def]}}}
              expr (c/resolve-refs (c/compile ctx elm) {"x" expr-def})]
          (has-form expr '(for-each [1] (current "A") "A"))))

      (testing "resolve parameters"
        (has-form (c/resolve-params expr {"x" [1]})
          '(for-each [1] (param-ref "y") "A")))

      (testing "optimize"
        (let [elm {:type "ForEach"
                   :source #ctu/optimizeable "x"
                   :element #elm/current "A"
                   :scope "A"}
              expr (st/with-instrument-disabled (c/optimize (c/compile {} elm) nil))]
          (has-form expr '(for-each (optimized "x") (current "A") "A"))))

      (testing "form"
        (has-form expr '(for-each (param-ref "x") (param-ref "y") "A")))

      (ctu/testing-equals-hash-code {:type "ForEach"
                                     :source #elm/parameter-ref "x"
                                     :element #elm/parameter-ref "y"
                                     :scope "A"})))

  (testing "without scope"
    (let [expr (ctu/dynamic-compile {:type "ForEach"
                                     :source #elm/parameter-ref "x"
                                     :element #elm/parameter-ref "y"})]

      (testing "expression is dynamic"
        (is (false? (core/-static expr))))

      (ctu/testing-constant-attach-cache expr)

      (ctu/testing-constant-patient-count expr)

      (testing "resolve expression references"
        (let [elm {:type "ForEach"
                   :source #elm/expression-ref "x"
                   :element #elm/current nil}
              expr-defs {:type "ExpressionDef" :name "x" :expression [1]
                         :context "Patient"}
              ctx {:library {:statements {:def [expr-defs]}}}
              expr (c/resolve-refs (c/compile ctx elm) {"x" expr-defs})]
          (has-form expr '(for-each [1] current))))

      (testing "resolve parameters"
        (has-form (c/resolve-params expr {"x" [1]})
          '(for-each [1] (param-ref "y"))))

      (testing "optimize"
        (let [elm {:type "ForEach"
                   :source #ctu/optimizeable "x"
                   :element #elm/current nil}
              expr (st/with-instrument-disabled (c/optimize (c/compile {} elm) nil))]
          (has-form expr '(for-each (optimized "x") current))))

      (testing "form"
        (has-form expr '(for-each (param-ref "x") (param-ref "y"))))

      (ctu/testing-equals-hash-code {:type "ForEach"
                                     :source #elm/parameter-ref "x"
                                     :element #elm/parameter-ref "y"}))))

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
  (are [source element res] (= res (core/-eval (c/compile {} (elm/index-of [source element])) {} nil nil))
    #elm/list [] #elm/integer "1" -1
    #elm/list [#elm/integer "1"] #elm/integer "1" 0
    #elm/list [#elm/integer "1" #elm/integer "1"] #elm/integer "1" 0
    #elm/list [#elm/integer "1" #elm/integer "2"] #elm/integer "2" 1

    #elm/list [] {:type "Null"} nil
    {:type "Null"} #elm/integer "1" nil
    {:type "Null"} {:type "Null"} nil)

  (ctu/testing-binary-dynamic-null elm/index-of #elm/list [] #elm/integer "1")

  (ctu/testing-binary-op elm/index-of))

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
  (testing "Static"
    (are [source res] (= res (core/-eval (c/compile {} (elm/last source)) {} nil nil))
      #elm/list [#elm/integer "1"] 1
      #elm/list [#elm/integer "1" #elm/integer "2"] 2))

  (testing "Dynamic"
    (are [source res] (= res (ctu/dynamic-compile-eval (elm/last source)))
      #elm/parameter-ref "[1]" 1
      #elm/parameter-ref "[1 2]" 2))

  (ctu/testing-unary-null elm/last)

  (ctu/testing-unary-op elm/last)

  (ctu/testing-optimize elm/last
    (testing "[1 2]"
      #ctu/optimize-to [1 2]
      2)))

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
    #elm/list [#elm/integer "1"] 1
    {:type "Null"} nil)

  (let [expr #elm/singleton-from #elm/list [#elm/integer "1" #elm/integer "1"]]
    (given-thrown (core/-eval (c/compile {} expr) {} nil nil)
      :message := "More than one element in `SingletonFrom` expression."
      [:expression :type] := "SingletonFrom"
      [:expression :operand :type] := "List"))

  (ctu/testing-unary-null elm/singleton-from)

  (ctu/testing-unary-op elm/singleton-from))

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
    #elm/list [#elm/integer "1"] #elm/integer "0" #elm/integer "1" [1]
    #elm/list [#elm/integer "1" #elm/integer "2"] #elm/integer "0" #elm/integer "1" [1]
    #elm/list [#elm/integer "1" #elm/integer "2"] #elm/integer "1" #elm/integer "2" [2]
    #elm/list [#elm/integer "1" #elm/integer "2" #elm/integer "3"] #elm/integer "1" #elm/integer "3" [2 3]
    #elm/list [#elm/integer "1" #elm/integer "2"] {:type "Null"} {:type "Null"} [1 2]

    #elm/list [#elm/integer "1"] #elm/integer "-1" #elm/integer "0" []
    #elm/list [#elm/integer "1"] #elm/integer "1" #elm/integer "0" []

    {:type "Null"} #elm/integer "0" #elm/integer "0" nil
    {:type "Null"} {:type "Null"} {:type "Null"} nil)

  (ctu/testing-ternary-op elm/slice))

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
    #elm/list [#elm/integer "2" #elm/integer "1"]
    {:type "ByDirection" :direction "asc"} [1 2]

    #elm/list [#elm/integer "1" #elm/integer "2"]
    {:type "ByDirection" :direction "desc"} [2 1]

    {:type "Null"} {:type "ByDirection" :direction "asc"} nil)

  (let [expr (ctu/dynamic-compile {:type "Sort"
                                   :source #elm/parameter-ref "x"
                                   :by [{:type "ByDirection" :direction "asc"}]})]

    (testing "expression is dynamic"
      (is (false? (core/-static expr))))

    (testing "form"
      (has-form expr '(sort (param-ref "x") :asc)))))

;; 20.28. Times
;;
;; The Times operator performs the cartesian product of two lists of tuples.
;; The return type of a Times operator is a tuple with all the components from
;; the tuple types of both arguments. The result will contain a tuple for each
;; possible combination of tuples from both arguments with the values for each
;; component derived from the pairing of the source tuples.
;;
;; If either argument is null, the result is null.
(deftest compile-times-test
  (are [x y res] (= res (ctu/compile-binop elm/times elm/list x y))
    [#elm/tuple{"id" #elm/integer "1"}] [#elm/tuple{"name" #elm/string "john"}]
    [{:id 1 :name "john"}]

    [#elm/tuple{"id" #elm/integer "1"}
     #elm/tuple{"id" #elm/integer "2"}]
    [#elm/tuple{"name" #elm/string "john"}
     #elm/tuple{"name" #elm/string "hans"}]
    [{:id 1 :name "john"}
     {:id 2 :name "john"}
     {:id 1 :name "hans"}
     {:id 2 :name "hans"}]

    [#elm/tuple{"id" #elm/integer "1"}
     #elm/tuple{"id" #elm/integer "2"}
     #elm/tuple{"id" #elm/integer "3"}]
    [#elm/tuple{"name" #elm/string "john"}
     #elm/tuple{"name" #elm/string "hans"}
     #elm/tuple{"name" #elm/string "tim"}]
    [{:id 1 :name "john"} {:id 2 :name "john"} {:id 3 :name "john"}
     {:id 1 :name "hans"} {:id 2 :name "hans"} {:id 3 :name "hans"}
     {:id 1 :name "tim"} {:id 2 :name "tim"} {:id 3 :name "tim"}]

    [#elm/tuple{"id" #elm/integer "1"}
     #elm/tuple{"id" #elm/integer "2"}]
    [#elm/tuple{"given-name" #elm/string "john"
                "family-name" #elm/string "doe"}
     #elm/tuple{"given-name" #elm/string "hans"
                "family-name" #elm/string "zimmer"}]
    [{:id 1 :given-name "john" :family-name "doe"}
     {:id 2 :given-name "john" :family-name "doe"}
     {:id 1 :given-name "hans" :family-name "zimmer"}
     {:id 2 :given-name "hans" :family-name "zimmer"}]

    [#elm/tuple{"id" #elm/integer "1"
                "name" #elm/string "john"}
     #elm/tuple{"id" #elm/integer "2"
                "name" #elm/string "hans"}]
    [#elm/tuple{"location" #elm/string "Frankfurt"}
     #elm/tuple{"location" #elm/string "Berlin"}]
    [{:id 1 :name "john" :location "Frankfurt"}
     {:id 2 :name "hans" :location "Frankfurt"}
     {:id 1 :name "john" :location "Berlin"}
     {:id 2 :name "hans" :location "Berlin"}])

  (ctu/testing-binary-null elm/times #elm/list[#elm/tuple{"name" #elm/string "hans"}])

  (ctu/testing-binary-op elm/times))

;; 20.29. Union
;;
;; See 19.31. Union
