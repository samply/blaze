(ns blaze.elm.compiler.conditional-operators-test
  "15. Conditional Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.db.api-stub :as api-stub]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.test-util :as ctu :refer [has-form]]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.literal-spec]
   [blaze.module.test-util :refer [with-system]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)
(ctu/instrument-compile)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

(defn- from-names [names]
  (mapv
   (fn [n]
     {:type "ExpressionDef" :name n
      :expression n
      :context "Unfiltered"})
   names))

(defn- index-by-name [expr-defs]
  (into {} (map (fn [{:keys [name] :as expr-def}] [name expr-def])) expr-defs))

;; 15.1. Case
;;
;; The Case operator allows for multiple conditional expressions to be chained
;; together in a single expression, rather than having to nest multiple If
;; operators. In addition, the comparand operand provides a variant on the case
;; that allows a single value to be compared in each conditional.
;;
;; If a comparand is not provided, the type of each when element of the
;; caseItems within the Case is expected to be boolean. If a comparand is
;; provided, the type of each when element of the caseItems within the Case is
;; expected to be of the same type as the comparand. An else element must always
;; be provided.
;;
;; The static type of the then argument within the first caseItem determines the
;; type of the result, and the then argument of each subsequent caseItem and the
;; else argument must be of that same type.
(deftest compile-case-test
  ;; Case is only implemented dynamically
  (testing "Dynamic"
    (testing "multi-conditional"
      (are [when res] (= res (ctu/dynamic-compile-eval
                              {:type "Case"
                               :caseItem
                               [{:when when
                                 :then #elm/integer "1"}]
                               :else #elm/integer "2"}))

        #elm/parameter-ref "true" 1
        #elm/parameter-ref "false" 2
        #elm/parameter-ref "nil" 2))

    (testing "comparand-based"
      (are [comparand res] (= res (ctu/dynamic-compile-eval
                                   {:type "Case"
                                    :comparand comparand
                                    :caseItem
                                    [{:when #elm/string "a"
                                      :then #elm/integer "1"}]
                                    :else #elm/integer "2"}))

        #elm/parameter-ref "a" 1
        #elm/parameter-ref "b" 2
        #elm/parameter-ref "nil" 2)))

  (testing "form"
    (testing "Static"
      (testing "multi-conditional"
        (let [expr (c/compile {} {:type "Case"
                                  :caseItem
                                  [{:when #elm/boolean "true"
                                    :then #elm/integer "1"}]
                                  :else #elm/integer "2"})]
          (has-form expr '(case true 1 2))))

      (testing "comparand-based"
        (let [expr (c/compile {} {:type "Case"
                                  :comparand #elm/string "a"
                                  :caseItem
                                  [{:when #elm/string "b"
                                    :then #elm/integer "1"}]
                                  :else #elm/integer "2"})]
          (has-form expr '(case "a" "b" 1 2)))))

    (testing "Dynamic"
      (testing "multi-conditional"
        (let [expr (ctu/dynamic-compile
                    {:type "Case"
                     :caseItem
                     [{:when #elm/parameter-ref "x"
                       :then #elm/parameter-ref "y"}]
                     :else #elm/parameter-ref "z"})]
          (has-form expr '(case (param-ref "x") (param-ref "y") (param-ref "z")))))

      (testing "comparand-based"
        (let [expr (ctu/dynamic-compile
                    {:type "Case"
                     :comparand #elm/parameter-ref "a"
                     :caseItem
                     [{:when #elm/parameter-ref "x"
                       :then #elm/parameter-ref "y"}]
                     :else #elm/parameter-ref "z"})]
          (has-form expr '(case (param-ref "a") (param-ref "x") (param-ref "y") (param-ref "z")))))))

  (testing "expression is dynamic"
    (testing "multi-conditional"
      (is (false? (core/-static (ctu/dynamic-compile {:type "Case"
                                                      :caseItem
                                                      [{:when #elm/parameter-ref "true"
                                                        :then #elm/parameter-ref "1"}]
                                                      :else #elm/parameter-ref "2"})))))

    (testing "comparand-based"
      (is (false? (core/-static (ctu/dynamic-compile {:type "Case"
                                                      :comparand #elm/parameter-ref "a"
                                                      :caseItem
                                                      [{:when #elm/parameter-ref "b"
                                                        :then #elm/parameter-ref "1"}]
                                                      :else #elm/parameter-ref "2"}))))))

  (testing "attach cache"
    (testing "multi-conditional"
      (with-redefs [ec/get #(do (assert (= ::cache %1)) (c/form %2))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm {:type "Case"
                     :caseItem
                     [{:when #elm/exists #elm/retrieve{:type "Encounter"}
                       :then #elm/exists #elm/retrieve{:type "Observation"}}]
                     :else #elm/exists #elm/retrieve{:type "Condition"}}
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]

            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0] := expr
              [1 count] := 3
              [1 0] := '(exists (retrieve "Encounter"))
              [1 1] := '(exists (retrieve "Observation"))
              [1 2] := '(exists (retrieve "Condition")))))))

    (testing "comparand-based"
      (with-redefs [ec/get #(do (assert (= ::cache %1)) (c/form %2))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm {:type "Case"
                     :comparand #elm/exists #elm/retrieve{:type "Encounter"}
                     :caseItem
                     [{:when #elm/exists #elm/retrieve{:type "Observation"}
                       :then #elm/exists #elm/retrieve{:type "Condition"}}]
                     :else #elm/exists #elm/retrieve{:type "MedicationAdministration"}}
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]

            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0] := expr
              [1 count] := 4
              [1 0] := '(exists (retrieve "Encounter"))
              [1 1] := '(exists (retrieve "Observation"))
              [1 2] := '(exists (retrieve "Condition"))
              [1 3] := '(exists (retrieve "MedicationAdministration"))))))))

  (testing "resolve expression references"
    (testing "multi-conditional"
      (let [elm {:type "Case"
                 :caseItem
                 [{:when #elm/expression-ref "w"
                   :then #elm/expression-ref "t"}]
                 :else #elm/expression-ref "e"}
            expr-defs (from-names ["w" "t" "e"])
            ctx {:library {:statements {:def expr-defs}}}
            expr (c/resolve-refs (c/compile ctx elm) (index-by-name expr-defs))]
        (has-form expr '(case "w" "t" "e"))))

    (testing "comparand-based"
      (let [elm {:type "Case"
                 :comparand #elm/expression-ref "c"
                 :caseItem
                 [{:when #elm/expression-ref "w"
                   :then #elm/expression-ref "t"}]
                 :else #elm/expression-ref "e"}
            expr-defs (from-names ["c" "w" "t" "e"])
            ctx {:library {:statements {:def expr-defs}}}
            expr (c/resolve-refs (c/compile ctx elm) (index-by-name expr-defs))]
        (has-form expr '(case "c" "w" "t" "e")))))

  (testing "resolve parameters"
    (testing "multi-conditional"
      (let [elm {:type "Case"
                 :caseItem
                 [{:when #elm/parameter-ref "w"
                   :then #elm/parameter-ref "t"}]
                 :else #elm/parameter-ref "e"}
            ctx {:library {:parameters {:def [{:name "w"} {:name "t"} {:name "e"}]}}}
            expr (c/resolve-params (c/compile ctx elm) {"w" "w" "t" "t" "e" "e"})]
        (has-form expr '(case "w" "t" "e"))))

    (testing "comparand-based"
      (let [elm {:type "Case"
                 :comparand #elm/parameter-ref "c"
                 :caseItem
                 [{:when #elm/parameter-ref "w"
                   :then #elm/parameter-ref "t"}]
                 :else #elm/parameter-ref "e"}
            ctx {:library {:parameters {:def [{:name "c"} {:name "w"} {:name "t"} {:name "e"}]}}}
            expr (c/resolve-params (c/compile ctx elm) {"c" "c" "w" "w" "t" "t" "e" "e"})]
        (has-form expr '(case "c" "w" "t" "e")))))

  (testing "optimize"
    (testing "multi-conditional"
      (let [elm {:type "Case"
                 :caseItem
                 [{:when #ctu/optimizeable "w"
                   :then #ctu/optimizeable "t"}]
                 :else #ctu/optimizeable "e"}
            expr (st/with-instrument-disabled (c/optimize (c/compile {} elm) nil))]
        (has-form expr '(case (optimized "w") (optimized "t") (optimized "e")))))

    (testing "comparand-based"
      (let [elm {:type "Case"
                 :comparand #ctu/optimizeable "c"
                 :caseItem
                 [{:when #ctu/optimizeable "w"
                   :then #ctu/optimizeable "t"}]
                 :else #ctu/optimizeable "e"}
            expr (st/with-instrument-disabled (c/optimize (c/compile {} elm) nil))]
        (has-form expr '(case (optimized "c") (optimized "w") (optimized "t") (optimized "e"))))))

  (testing "equals/hashCode"
    (testing "multi-conditional"
      (let [elm {:type "Case"
                 :caseItem
                 [{:when #elm/parameter-ref "w"
                   :then #elm/parameter-ref "t"}]
                 :else #elm/parameter-ref "e"}
            ctx {:library {:parameters {:def [{:name "w"} {:name "t"} {:name "e"}]}}}
            expr-1 (c/compile ctx elm)
            expr-2 (c/compile ctx elm)]
        (is (= 1 (count (set [expr-1 expr-2]))))))

    (testing "comparand-based"
      (let [elm {:type "Case"
                 :comparand #elm/parameter-ref "c"
                 :caseItem
                 [{:when #elm/parameter-ref "w"
                   :then #elm/parameter-ref "t"}]
                 :else #elm/parameter-ref "e"}
            ctx {:library {:parameters {:def [{:name "c"} {:name "w"} {:name "t"} {:name "e"}]}}}
            expr-1 (c/compile ctx elm)
            expr-2 (c/compile ctx elm)]
        (is (= 1 (count (set [expr-1 expr-2]))))))))

;; 15.2. If
;;
;; The If operator evaluates a condition, and returns the then argument if the
;; condition evaluates to true; if the condition evaluates to false or null, the
;; result of the else argument is returned. The static type of the then argument
;; determines the result type of the conditional, and the else argument must be
;; of that same type.
(deftest compile-if-test
  (testing "Static"
    (are [elm res] (= res (c/compile {} elm))
      #elm/if [#elm/boolean "true" #elm/integer "1" #elm/integer "2"] 1
      #elm/if [#elm/boolean "false" #elm/integer "1" #elm/integer "2"] 2
      #elm/if [{:type "Null"} #elm/integer "1" #elm/integer "2"] 2))

  (testing "Dynamic"
    (are [elm res] (= res (ctu/dynamic-compile-eval elm))
      #elm/if [#elm/parameter-ref "true" #elm/integer "1" #elm/integer "2"] 1
      #elm/if [#elm/parameter-ref "false" #elm/integer "1" #elm/integer "2"] 2
      #elm/if [#elm/parameter-ref "nil" #elm/integer "1" #elm/integer "2"] 2))

  (testing "expression is dynamic"
    (is (false? (core/-static (ctu/dynamic-compile #elm/if [#elm/parameter-ref "x"
                                                            #elm/parameter-ref "y"
                                                            #elm/parameter-ref "z"])))))

  (testing "form"
    (let [expr (c/compile {} #elm/if [#elm/boolean "true" #elm/integer "1" #elm/integer "2"])]
      (has-form expr 1))

    (let [expr (c/compile {} #elm/if [#elm/boolean "false" #elm/integer "1" #elm/integer "2"])]
      (has-form expr 2))

    (let [expr (ctu/dynamic-compile #elm/if [#elm/parameter-ref "x"
                                             #elm/parameter-ref "y"
                                             #elm/parameter-ref "z"])]
      (has-form expr '(if (param-ref "x") (param-ref "y") (param-ref "z")))))

  (testing "attach cache"
    (with-redefs [ec/get #(do (assert (= ::cache %1)) %2)]
      (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
        (let [elm #elm/if [#elm/exists #elm/retrieve{:type "Encounter"}
                           #elm/exists #elm/retrieve{:type "Observation"}
                           #elm/exists #elm/retrieve{:type "Condition"}]
              ctx {:node node :eval-context "Patient"}
              expr (c/compile ctx elm)]
          (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
            count := 2
            [0] := expr
            [1 count] := 3
            [1 0 c/form] := '(exists (retrieve "Encounter"))
            [1 1 c/form] := '(exists (retrieve "Observation"))
            [1 2 c/form] := '(exists (retrieve "Condition")))))))

  (testing "resolve expression references"
    (let [elm #elm/if [#elm/expression-ref "c"
                       #elm/expression-ref "t"
                       #elm/expression-ref "e"]
          expr-defs (from-names ["c" "t" "e"])
          ctx {:library {:statements {:def expr-defs}}}
          expr (c/resolve-refs (c/compile ctx elm) (index-by-name expr-defs))]
      (has-form expr '(if "c" "t" "e"))))

  (testing "resolve parameters"
    (let [elm #elm/if [#elm/parameter-ref "c"
                       #elm/parameter-ref "t"
                       #elm/parameter-ref "e"]
          ctx {:library {:parameters {:def [{:name "c"} {:name "t"} {:name "e"}]}}}
          expr (c/resolve-params (c/compile ctx elm) {"c" "c" "t" "t" "e" "e"})]
      (has-form expr '(if "c" "t" "e"))))

  (testing "optimize"
    (let [elm #elm/if [#ctu/optimizeable "c"
                       #ctu/optimizeable "t"
                       #ctu/optimizeable "e"]
          expr (st/with-instrument-disabled (c/optimize (c/compile {} elm) nil))]
      (has-form expr '(if (optimized "c") (optimized "t") (optimized "e")))))

  (ctu/testing-equals-hash-code #elm/if [#elm/parameter-ref "x"
                                         #elm/parameter-ref "y"
                                         #elm/parameter-ref "z"]))
