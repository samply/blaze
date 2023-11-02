(ns blaze.elm.compiler.conditional-operators-test
  "15. Conditional Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu :refer [has-form]]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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
      (are [when res] (= res (tu/dynamic-compile-eval
                               {:type "Case"
                                :caseItem
                                [{:when when
                                  :then #elm/integer "1"}]
                                :else #elm/integer "2"}))

        #elm/parameter-ref "true" 1
        #elm/parameter-ref "false" 2
        #elm/parameter-ref "nil" 2))

    (testing "comparand-based"
      (are [comparand res] (= res (tu/dynamic-compile-eval
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
        (let [expr (tu/dynamic-compile
                     {:type "Case"
                      :caseItem
                      [{:when #elm/parameter-ref "x"
                        :then #elm/parameter-ref "y"}]
                      :else #elm/parameter-ref "z"})]
          (has-form expr '(case (param-ref "x") (param-ref "y") (param-ref "z")))))

      (testing "comparand-based"
        (let [expr (tu/dynamic-compile
                     {:type "Case"
                      :comparand #elm/parameter-ref "a"
                      :caseItem
                      [{:when #elm/parameter-ref "x"
                        :then #elm/parameter-ref "y"}]
                      :else #elm/parameter-ref "z"})]
          (has-form expr '(case (param-ref "a") (param-ref "x") (param-ref "y") (param-ref "z")))))))

  (testing "expression is dynamic"
    (testing "multi-conditional"
      (is (false? (core/-static (tu/dynamic-compile {:type "Case"
                                                     :caseItem
                                                     [{:when #elm/parameter-ref "true"
                                                       :then #elm/parameter-ref "1"}]
                                                     :else #elm/parameter-ref "2"})))))

    (testing "comparand-based"
      (is (false? (core/-static (tu/dynamic-compile {:type "Case"
                                                     :comparand #elm/parameter-ref "a"
                                                     :caseItem
                                                     [{:when #elm/parameter-ref "b"
                                                       :then #elm/parameter-ref "1"}]
                                                     :else #elm/parameter-ref "2"})))))))


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
    (are [elm res] (= res (tu/dynamic-compile-eval elm))
      #elm/if [#elm/parameter-ref "true" #elm/integer "1" #elm/integer "2"] 1
      #elm/if [#elm/parameter-ref "false" #elm/integer "1" #elm/integer "2"] 2
      #elm/if [#elm/parameter-ref "nil" #elm/integer "1" #elm/integer "2"] 2))

  (testing "expression is dynamic"
    (is (false? (core/-static (tu/dynamic-compile #elm/if [#elm/parameter-ref "x"
                                                           #elm/parameter-ref "y"
                                                           #elm/parameter-ref "z"])))))

  (testing "form"
    (let [expr (c/compile {} #elm/if [#elm/boolean "true" #elm/integer "1" #elm/integer "2"])]
      (has-form expr 1))

    (let [expr (c/compile {} #elm/if [#elm/boolean "false" #elm/integer "1" #elm/integer "2"])]
      (has-form expr 2))

    (let [expr (tu/dynamic-compile #elm/if [#elm/parameter-ref "x"
                                            #elm/parameter-ref "y"
                                            #elm/parameter-ref "z"])]
      (has-form expr '(if (param-ref "x") (param-ref "y") (param-ref "z"))))))
