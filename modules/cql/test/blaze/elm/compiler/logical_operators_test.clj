(ns blaze.elm.compiler.logical-operators-test
  "13. Logical Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.api-stub :as api-stub]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.logical-operators :as ops]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.compiler.test-util :as ctu]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
   [blaze.elm.literal :as elm]
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

;; 13. Logical Operators

;; 13.1. And
;;
;; The And operator returns the logical conjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is false, the result is false; if both arguments are true,
;; the result is true; otherwise, the result is null. Note also that ELM does
;; not prescribe short-circuit evaluation.
(deftest compile-and-test
  (testing "Static"
    (are [x y pred] (pred (c/compile {} (elm/and [x y])))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" false?
      #elm/boolean "true" {:type "Null"} nil?

      #elm/boolean "false" #elm/boolean "true" false?
      #elm/boolean "false" #elm/boolean "false" false?
      #elm/boolean "false" {:type "Null"} false?

      {:type "Null"} #elm/boolean "true" nil?
      {:type "Null"} #elm/boolean "false" false?
      {:type "Null"} {:type "Null"} nil?))

  (testing "Dynamic"
    (are [x y pred] (pred (ctu/dynamic-compile-eval (elm/and [x y])))
      #elm/boolean "true" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" #elm/boolean "true" true?
      #elm/parameter-ref "true" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" {:type "Null"} nil?
      {:type "Null"} #elm/parameter-ref "true" nil?

      #elm/boolean "true" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" #elm/boolean "true" false?
      #elm/parameter-ref "false" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" {:type "Null"} false?
      {:type "Null"} #elm/parameter-ref "false" false?

      #elm/boolean "false" #elm/parameter-ref "nil" false?
      #elm/parameter-ref "nil" #elm/boolean "false" false?
      #elm/boolean "true" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/boolean "true" nil?
      #elm/parameter-ref "nil" #elm/parameter-ref "nil" nil?))

  (testing "form"
    (are [x y form] (= form (c/form (ctu/dynamic-compile (elm/and [x y]))))
      #elm/boolean "true" #elm/boolean "true" true
      #elm/boolean "true" #elm/boolean "false" false
      #elm/boolean "true" {:type "Null"} nil
      #elm/boolean "true" #elm/parameter-ref "b" '(param-ref "b")

      #elm/boolean "false" #elm/boolean "true" false
      #elm/boolean "false" #elm/boolean "false" false
      #elm/boolean "false" {:type "Null"} false
      #elm/boolean "false" #elm/parameter-ref "b" false

      {:type "Null"} #elm/boolean "true" nil
      {:type "Null"} #elm/boolean "false" false
      {:type "Null"} {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref "b" '(and nil (param-ref "b"))

      #elm/parameter-ref "a" #elm/boolean "true" '(param-ref "a")
      #elm/parameter-ref "a" #elm/boolean "false" false
      #elm/parameter-ref "a" {:type "Null"} '(and nil (param-ref "a"))
      #elm/parameter-ref "a" #elm/parameter-ref "b" '(and (param-ref "a") (param-ref "b"))))

  (testing "Static"
    (are [x y pred] (pred (core/-static (ctu/dynamic-compile (elm/and [x y]))))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} true?
      #elm/boolean "true" #elm/parameter-ref "b" false?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" true?
      #elm/boolean "false" {:type "Null"} true?
      #elm/boolean "false" #elm/parameter-ref "b" true?

      {:type "Null"} #elm/boolean "true" true?
      {:type "Null"} #elm/boolean "false" true?
      {:type "Null"} {:type "Null"} true?
      {:type "Null"} #elm/parameter-ref "b" false?

      #elm/parameter-ref "a" #elm/boolean "true" false?
      #elm/parameter-ref "a" #elm/boolean "false" true?
      #elm/parameter-ref "a" {:type "Null"} false?
      #elm/parameter-ref "a" #elm/parameter-ref "b" false?))

  (testing "attach cache"
    (testing "only one bloom filter available"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (when-not (= '(exists (retrieve "Observation")) (c/form expr))
                               (bloom-filter/build-bloom-filter expr 0 nil)))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/and [#elm/exists #elm/retrieve{:type "Observation"}
                              #elm/exists #elm/retrieve{:type "Condition"}]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0 c/form] := '(and (exists (retrieve "Condition"))
                                  (exists (retrieve "Observation")))
              [1 count] := 2
              [1 0 ::bloom-filter/expr-form] := "(exists (retrieve \"Condition\"))"
              [1 1] := (ba/unavailable "No Bloom filter available."))))))

    (testing "both bloom filters available with first having more patients"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (cond
                               (= (c/form expr) '(exists (retrieve "Observation")))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1"])
                               (= (c/form expr) '(exists (retrieve "Condition")))
                               (bloom-filter/build-bloom-filter expr 0 ["0"])))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/and [#elm/exists #elm/retrieve{:type "Condition"}
                              #elm/exists #elm/retrieve{:type "Observation"}]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0 c/form] := '(and (exists (retrieve "Condition"))
                                  (exists (retrieve "Observation")))
              [1 count] := 2
              [1 0 ::bloom-filter/patient-count] := 1
              [1 1 ::bloom-filter/patient-count] := 2)))))

    (testing "with three expressions"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (condp = (c/form expr)
                               '(exists (retrieve "Observation"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1"])
                               '(exists (retrieve "Condition"))
                               (bloom-filter/build-bloom-filter expr 0 ["0"])
                               '(exists (retrieve "Specimen"))
                               nil))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/and [#elm/exists #elm/retrieve{:type "Observation"}
                              #elm/and [#elm/exists #elm/retrieve{:type "Condition"}
                                        #elm/exists #elm/retrieve{:type "Specimen"}]]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0 c/form] := '(and (exists (retrieve "Condition"))
                                  (exists (retrieve "Observation"))
                                  (exists (retrieve "Specimen")))
              [1 count] := 3
              [1 0 ::bloom-filter/patient-count] := 1
              [1 1 ::bloom-filter/patient-count] := 2
              [1 2 ::bloom-filter/patient-count] := nil)))))

    (testing "with four expressions"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (condp = (c/form expr)
                               '(exists (retrieve "Observation"))
                               nil
                               '(exists (retrieve "Condition"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1" "2"])
                               '(exists (retrieve "Specimen"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1"])
                               '(exists (retrieve "MedicationAdministration"))
                               (bloom-filter/build-bloom-filter expr 0 ["0"])))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/and [#elm/and [#elm/exists #elm/retrieve{:type "MedicationAdministration"}
                                        #elm/exists #elm/retrieve{:type "Specimen"}]
                              #elm/and [#elm/exists #elm/retrieve{:type "Condition"}
                                        #elm/exists #elm/retrieve{:type "Observation"}]]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0 c/form] := '(and (exists (retrieve "MedicationAdministration"))
                                  (exists (retrieve "Specimen"))
                                  (exists (retrieve "Condition"))
                                  (exists (retrieve "Observation")))
              [1 count] := 4
              [1 0 ::bloom-filter/patient-count] := 1
              [1 1 ::bloom-filter/patient-count] := 2
              [1 2 ::bloom-filter/patient-count] := 3
              [1 3 ::bloom-filter/patient-count] := nil)))))

    (testing "with five expressions"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (condp = (c/form expr)
                               '(exists (retrieve "Observation"))
                               nil
                               '(exists (retrieve "Condition"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1" "2"])
                               '(exists (retrieve "Specimen"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1"])
                               '(exists (retrieve "MedicationAdministration"))
                               (bloom-filter/build-bloom-filter expr 0 ["0"])
                               '(exists (retrieve "Procedure"))
                               nil))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/and [#elm/and [#elm/exists #elm/retrieve{:type "MedicationAdministration"}
                                        #elm/and [#elm/exists #elm/retrieve{:type "Procedure"}
                                                  #elm/exists #elm/retrieve{:type "Specimen"}]]
                              #elm/and [#elm/exists #elm/retrieve{:type "Condition"}
                                        #elm/exists #elm/retrieve{:type "Observation"}]]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0 c/form] := '(and (exists (retrieve "MedicationAdministration"))
                                  (exists (retrieve "Specimen"))
                                  (exists (retrieve "Condition"))
                                  (exists (retrieve "Procedure"))
                                  (exists (retrieve "Observation")))
              [1 count] := 5
              [1 0 ::bloom-filter/patient-count] := 1
              [1 1 ::bloom-filter/patient-count] := 2
              [1 2 ::bloom-filter/patient-count] := 3
              [1 3 ::bloom-filter/patient-count] := nil
              [1 4 ::bloom-filter/patient-count] := nil))))))

  (ctu/testing-binary-op elm/and)

  (ctu/testing-optimize elm/and
    (testing "with one null operand"
      [#ctu/optimizeable "x" {:type "Null"}]
      [{:type "Null"} #ctu/optimizeable "x"]
      '(and nil (optimized "x")))

    (testing "with one null operand and the other operand optimizing to true"
      [#ctu/optimize-to true {:type "Null"}]
      [{:type "Null"} #ctu/optimize-to true]
      nil)

    (testing "with one null operand and the other operand optimizing to false"
      [#ctu/optimize-to false {:type "Null"}]
      [{:type "Null"} #ctu/optimize-to false]
      false)

    (testing "with one null operand and the other operand optimizing to nil"
      [#ctu/optimize-to nil {:type "Null"}]
      [{:type "Null"} #ctu/optimize-to nil]
      nil)

    (testing "with two null operands"
      [{:type "Null"} {:type "Null"}]
      nil)

    (testing "with one operand optimizing to true"
      [#ctu/optimize-to true #ctu/optimizeable "x"]
      [#ctu/optimizeable "x" #ctu/optimize-to true]
      '(optimized "x"))

    (testing "with both operands optimizing to true"
      [#ctu/optimize-to true #ctu/optimize-to true]
      true)

    (testing "with one operand optimizing to false"
      [#ctu/optimize-to false #ctu/optimizeable "x"]
      [#ctu/optimizeable "x" #ctu/optimize-to false]
      false)

    (testing "with one operand optimizing to nil"
      [#ctu/optimize-to nil #ctu/optimizeable "x"]
      [#ctu/optimizeable "x" #ctu/optimize-to nil]
      '(and nil (optimized "x")))))

(deftest and-op-patient-count-test
  (testing "both nil"
    (let [op (ops/and-op
              (reify-expr core/Expression)
              (reify-expr core/Expression))]
      (is (nil? (core/-patient-count op)))))

  (testing "first nil"
    (let [op (ops/and-op
              (reify-expr core/Expression)
              (reify-expr core/Expression
                (-patient-count [_] 0)))]
      (is (nil? (core/-patient-count op)))))

  (testing "second nil"
    (let [op (ops/and-op
              (reify-expr core/Expression
                (-patient-count [_] 0))
              (reify-expr core/Expression))]
      (is (nil? (core/-patient-count op)))))

  (testing "first smaller"
    (let [op (ops/and-op
              (reify-expr core/Expression
                (-patient-count [_] 1))
              (reify-expr core/Expression
                (-patient-count [_] 2)))]
      (is (= 1 (core/-patient-count op)))))

  (testing "second smaller"
    (let [op (ops/and-op
              (reify-expr core/Expression
                (-patient-count [_] 2))
              (reify-expr core/Expression
                (-patient-count [_] 1)))]
      (is (= 1 (core/-patient-count op))))))

;; 13.2. Implies
;;
;; Normalized to (Or (Not x) y)
(deftest compile-implies-test
  (ctu/unsupported-binary-operand "Implies"))

;; 13.3. Not
;;
;; The Not operator returns the logical negation of its argument. If the
;; argument is true, the result is false; if the argument is false, the result
;; is true; otherwise, the result is null.
(deftest compile-not-test
  (testing "Static"
    (are [x pred] (pred (c/compile {} (elm/not x)))
      #elm/boolean "true" false?
      #elm/boolean "false" true?))

  (testing "Dynamic"
    (are [x pred] (pred (ctu/dynamic-compile-eval (elm/not x)))
      #elm/parameter-ref "true" false?
      #elm/parameter-ref "false" true?))

  (ctu/testing-unary-null elm/not)

  (ctu/testing-unary-op elm/not)

  (ctu/testing-optimize elm/not
    (testing "with the operand optimizing to true"
      #ctu/optimize-to true
      false)

    (testing "with the operand optimizing to false"
      #ctu/optimize-to false
      true)

    (testing "with the operand optimizing to nil"
      #ctu/optimize-to nil
      nil)))

;; 13.4. Or
;;
;; The Or operator returns the logical disjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is true, the result is true; if both arguments are false, the
;; result is false; otherwise, the result is null. Note also that ELM does not
;; prescribe short-circuit evaluation.
(deftest compile-or-test
  (testing "Static"
    (are [x y pred] (pred (c/compile {} (elm/or [x y])))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} true?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" false?
      #elm/boolean "false" {:type "Null"} nil?

      {:type "Null"} #elm/boolean "true" true?
      {:type "Null"} #elm/boolean "false" nil?
      {:type "Null"} {:type "Null"} nil?))

  (testing "Dynamic"
    (are [x y pred] (pred (ctu/dynamic-compile-eval (elm/or [x y])))
      #elm/boolean "false" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" #elm/boolean "false" true?
      #elm/parameter-ref "true" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" {:type "Null"} true?
      {:type "Null"} #elm/parameter-ref "true" true?

      #elm/boolean "false" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" #elm/boolean "false" false?
      #elm/parameter-ref "false" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" {:type "Null"} nil?
      #elm/parameter-ref "false" #elm/parameter-ref "nil" nil?
      {:type "Null"} #elm/parameter-ref "false" nil?

      #elm/boolean "true" #elm/parameter-ref "nil" true?
      #elm/parameter-ref "nil" #elm/boolean "true" true?
      #elm/boolean "false" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/boolean "false" nil?
      #elm/parameter-ref "nil" #elm/parameter-ref "nil" nil?))

  (testing "form"
    (are [x y form] (= form (c/form (ctu/dynamic-compile (elm/or [x y]))))
      #elm/boolean "true" #elm/boolean "true" true
      #elm/boolean "true" #elm/boolean "false" true
      #elm/boolean "true" {:type "Null"} true
      #elm/boolean "true" #elm/parameter-ref "b" true

      #elm/boolean "false" #elm/boolean "true" true
      #elm/boolean "false" #elm/boolean "false" false
      #elm/boolean "false" {:type "Null"} nil
      #elm/boolean "false" #elm/parameter-ref "b" '(param-ref "b")

      {:type "Null"} #elm/boolean "true" true
      {:type "Null"} #elm/boolean "false" nil
      {:type "Null"} {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref "b" '(or nil (param-ref "b"))

      #elm/parameter-ref "a" #elm/boolean "true" true
      #elm/parameter-ref "a" #elm/boolean "false" '(param-ref "a")
      #elm/parameter-ref "a" {:type "Null"} '(or nil (param-ref "a"))
      #elm/parameter-ref "a" #elm/parameter-ref "b" '(or (param-ref "a") (param-ref "b"))))

  (testing "Static"
    (are [x y pred] (pred (core/-static (ctu/dynamic-compile (elm/or [x y]))))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} true?
      #elm/boolean "true" #elm/parameter-ref "b" true?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" true?
      #elm/boolean "false" {:type "Null"} true?
      #elm/boolean "false" #elm/parameter-ref "b" false?

      {:type "Null"} #elm/boolean "true" true?
      {:type "Null"} #elm/boolean "false" true?
      {:type "Null"} {:type "Null"} true?
      {:type "Null"} #elm/parameter-ref "b" false?

      #elm/parameter-ref "a" #elm/boolean "true" true?
      #elm/parameter-ref "a" #elm/boolean "false" false?
      #elm/parameter-ref "a" {:type "Null"} false?
      #elm/parameter-ref "a" #elm/parameter-ref "b" false?))

  (ctu/testing-binary-dynamic elm/or)

  (testing "attach cache"
    (testing "only one bloom filter available"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (when-not (= '(exists (retrieve "Observation")) (c/form expr))
                               (bloom-filter/build-bloom-filter expr 0 nil)))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/or [#elm/exists #elm/retrieve{:type "Observation"}
                             #elm/exists #elm/retrieve{:type "Condition"}]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0] := expr
              [1 count] := 2
              [1 0] := (ba/unavailable "No Bloom filter available.")
              [1 1 ::bloom-filter/expr-form] := "(exists (retrieve \"Condition\"))")))))

    (testing "both bloom filters available with second having more patients"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (cond
                               (= (c/form expr) '(exists (retrieve "Observation")))
                               (bloom-filter/build-bloom-filter expr 0 ["0"])
                               (= (c/form expr) '(exists (retrieve "Condition")))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1"])))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/or [#elm/exists #elm/retrieve{:type "Observation"}
                             #elm/exists #elm/retrieve{:type "Condition"}]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0 c/form] := '(or (exists (retrieve "Condition"))
                                 (exists (retrieve "Observation")))
              [1 count] := 2
              [1 0 ::bloom-filter/patient-count] := 2
              [1 1 ::bloom-filter/patient-count] := 1)))))

    (testing "with three expressions"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (condp = (c/form expr)
                               '(exists (retrieve "Observation"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1"])
                               '(exists (retrieve "Condition"))
                               (bloom-filter/build-bloom-filter expr 0 ["0"])
                               '(exists (retrieve "Specimen"))
                               nil))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/or [#elm/exists #elm/retrieve{:type "Observation"}
                             #elm/or [#elm/exists #elm/retrieve{:type "Condition"}
                                      #elm/exists #elm/retrieve{:type "Specimen"}]]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0 c/form] := '(or (exists (retrieve "Specimen"))
                                 (exists (retrieve "Observation"))
                                 (exists (retrieve "Condition")))
              [1 count] := 3
              [1 0 ::bloom-filter/patient-count] := nil
              [1 1 ::bloom-filter/patient-count] := 2
              [1 2 ::bloom-filter/patient-count] := 1)))))

    (testing "with four expressions"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (condp = (c/form expr)
                               '(exists (retrieve "Observation"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1" "2" "3"])
                               '(exists (retrieve "Condition"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1" "2"])
                               '(exists (retrieve "Specimen"))
                               nil
                               '(exists (retrieve "MedicationAdministration"))
                               (bloom-filter/build-bloom-filter expr 0 ["0"])))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/or [#elm/or [#elm/exists #elm/retrieve{:type "MedicationAdministration"}
                                      #elm/exists #elm/retrieve{:type "Specimen"}]
                             #elm/or [#elm/exists #elm/retrieve{:type "Condition"}
                                      #elm/exists #elm/retrieve{:type "Observation"}]]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0 c/form] := '(or (exists (retrieve "Specimen"))
                                 (exists (retrieve "Observation"))
                                 (exists (retrieve "Condition"))
                                 (exists (retrieve "MedicationAdministration")))
              [1 count] := 4
              [1 0 ::bloom-filter/patient-count] := nil
              [1 1 ::bloom-filter/patient-count] := 4
              [1 2 ::bloom-filter/patient-count] := 3
              [1 3 ::bloom-filter/patient-count] := 1)))))

    (testing "with five expressions"
      (with-redefs [ec/get (fn [cache expr]
                             (assert (= ::cache cache))
                             (condp = (c/form expr)
                               '(exists (retrieve "Observation"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1" "2" "3"])
                               '(exists (retrieve "Condition"))
                               (bloom-filter/build-bloom-filter expr 0 ["0" "1" "2"])
                               '(exists (retrieve "Specimen"))
                               nil
                               '(exists (retrieve "Procedure"))
                               nil
                               '(exists (retrieve "MedicationAdministration"))
                               (bloom-filter/build-bloom-filter expr 0 ["0"])))]
        (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
          (let [elm #elm/or [#elm/or [#elm/exists #elm/retrieve{:type "MedicationAdministration"}
                                      #elm/exists #elm/retrieve{:type "Specimen"}]
                             #elm/or [#elm/exists #elm/retrieve{:type "Condition"}
                                      #elm/or [#elm/exists #elm/retrieve{:type "Observation"}
                                               #elm/exists #elm/retrieve{:type "Procedure"}]]]
                ctx {:node node :eval-context "Patient"}
                expr (c/compile ctx elm)]
            (given (st/with-instrument-disabled (c/attach-cache expr ::cache))
              count := 2
              [0 c/form] := '(or (exists (retrieve "Specimen"))
                                 (exists (retrieve "Procedure"))
                                 (exists (retrieve "Observation"))
                                 (exists (retrieve "Condition"))
                                 (exists (retrieve "MedicationAdministration")))
              [1 count] := 5
              [1 0 ::bloom-filter/patient-count] := nil
              [1 1 ::bloom-filter/patient-count] := nil
              [1 2 ::bloom-filter/patient-count] := 4
              [1 3 ::bloom-filter/patient-count] := 3
              [1 4 ::bloom-filter/patient-count] := 1))))))

  (ctu/testing-binary-resolve-refs elm/or)

  (ctu/testing-binary-resolve-params elm/or)

  (ctu/testing-binary-optimize elm/or)

  (ctu/testing-optimize elm/or
    (testing "with one null operand"
      [#ctu/optimizeable "x" {:type "Null"}]
      [{:type "Null"} #ctu/optimizeable "x"]
      '(or nil (optimized "x")))

    (testing "with one null operand and the other operand optimizing to true"
      [#ctu/optimize-to true {:type "Null"}]
      [{:type "Null"} #ctu/optimize-to true]
      true)

    (testing "with one null operand and the other operand optimizing to false"
      [#ctu/optimize-to false {:type "Null"}]
      [{:type "Null"} #ctu/optimize-to false]
      nil)

    (testing "with one null operand and the other operand optimizing to nil"
      [#ctu/optimize-to nil {:type "Null"}]
      [{:type "Null"} #ctu/optimize-to nil]
      nil)

    (testing "with two null operands"
      [{:type "Null"} {:type "Null"}]
      nil)

    (testing "with the other operand optimizing to true"
      [#ctu/optimize-to true {:type "Null"}]
      [{:type "Null"} #ctu/optimize-to true]
      true)

    (testing "with one operand optimizing to true"
      [#ctu/optimize-to true #ctu/optimizeable "x"]
      [#ctu/optimizeable "x" #ctu/optimize-to true]
      true)

    (testing "with one operand optimizing to false"
      [#ctu/optimize-to false #ctu/optimizeable "x"]
      [#ctu/optimizeable "x" #ctu/optimize-to false]
      '(optimized "x"))

    (testing "with both operands optimizing to false"
      [#ctu/optimize-to false #ctu/optimize-to false]
      false)

    (testing "with one operand optimizing to nil"
      [#ctu/optimize-to nil #ctu/optimizeable "x"]
      [#ctu/optimizeable "x" #ctu/optimize-to nil]
      '(or nil (optimized "x"))))

  (ctu/testing-binary-equals-hash-code elm/or)

  (ctu/testing-binary-form elm/or))

(deftest or-op-patient-count-test
  (testing "both nil"
    (let [op (ops/or-op
              (reify-expr core/Expression)
              (reify-expr core/Expression))]
      (is (nil? (core/-patient-count op)))))

  (testing "first nil"
    (let [op (ops/or-op
              (reify-expr core/Expression)
              (reify-expr core/Expression
                (-patient-count [_] 0)))]
      (is (nil? (core/-patient-count op)))))

  (testing "second nil"
    (let [op (ops/or-op
              (reify-expr core/Expression
                (-patient-count [_] 0))
              (reify-expr core/Expression))]
      (is (nil? (core/-patient-count op)))))

  (testing "first larger"
    (let [op (ops/or-op
              (reify-expr core/Expression
                (-patient-count [_] 2))
              (reify-expr core/Expression
                (-patient-count [_] 1)))]
      (is (= 2 (core/-patient-count op)))))

  (testing "second larger"
    (let [op (ops/or-op
              (reify-expr core/Expression
                (-patient-count [_] 1))
              (reify-expr core/Expression
                (-patient-count [_] 2)))]
      (is (= 2 (core/-patient-count op))))))

;; 13.5. Xor
;;
;; The Xor operator returns the exclusive or of its arguments. Note that this
;; operator is defined using 3-valued logic semantics. This means that the
;; result is true if and only if one argument is true and the other is false,
;; and that the result is false if and only if both arguments are true or both
;; arguments are false. If either or both arguments are null, the result is
;; null.
(deftest compile-xor-test
  (testing "Static"
    (are [x y pred] (pred (c/compile {} (elm/xor [x y])))
      #elm/boolean "true" #elm/boolean "true" false?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} nil?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" false?
      #elm/boolean "false" {:type "Null"} nil?

      {:type "Null"} #elm/boolean "true" nil?
      {:type "Null"} #elm/boolean "false" nil?
      {:type "Null"} {:type "Null"} nil?))

  (testing "Dynamic"
    (are [x y pred] (pred (ctu/dynamic-compile-eval (elm/xor [x y])))
      #elm/boolean "true" #elm/parameter-ref "true" false?
      #elm/parameter-ref "true" #elm/boolean "true" false?
      #elm/boolean "false" #elm/parameter-ref "true" true?
      #elm/parameter-ref "true" #elm/boolean "false" true?
      #elm/parameter-ref "true" #elm/parameter-ref "true" false?

      #elm/boolean "true" #elm/parameter-ref "false" true?
      #elm/parameter-ref "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/parameter-ref "false" false?
      #elm/parameter-ref "false" #elm/boolean "false" false?
      #elm/parameter-ref "false" #elm/parameter-ref "false" false?

      #elm/boolean "true" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/boolean "true" nil?
      #elm/parameter-ref "true" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/parameter-ref "true" nil?
      #elm/boolean "false" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/boolean "false" nil?
      #elm/parameter-ref "false" #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" #elm/parameter-ref "false" nil?
      {:type "Null"} #elm/parameter-ref "nil" nil?
      #elm/parameter-ref "nil" {:type "Null"} nil?
      #elm/parameter-ref "nil" #elm/parameter-ref "nil" nil?))

  (testing "form"
    (are [x y form] (= form (c/form (ctu/dynamic-compile (elm/xor [x y]))))
      #elm/boolean "true" #elm/boolean "true" false
      #elm/boolean "true" #elm/boolean "false" true
      #elm/boolean "true" {:type "Null"} nil
      #elm/boolean "true" #elm/parameter-ref "b" '(not (param-ref "b"))

      #elm/boolean "false" #elm/boolean "true" true
      #elm/boolean "false" #elm/boolean "false" false
      #elm/boolean "false" {:type "Null"} nil
      #elm/boolean "false" #elm/parameter-ref "b" '(param-ref "b")

      {:type "Null"} #elm/boolean "true" nil
      {:type "Null"} #elm/boolean "false" nil
      {:type "Null"} {:type "Null"} nil
      {:type "Null"} #elm/parameter-ref "b" nil

      #elm/parameter-ref "a" #elm/boolean "true" '(not (param-ref "a"))
      #elm/parameter-ref "a" #elm/boolean "false" '(param-ref "a")
      #elm/parameter-ref "a" {:type "Null"} nil
      #elm/parameter-ref "a" #elm/parameter-ref "b" '(xor (param-ref "a") (param-ref "b"))))

  (testing "Static"
    (are [x y pred] (pred (core/-static (ctu/dynamic-compile (elm/xor [x y]))))
      #elm/boolean "true" #elm/boolean "true" true?
      #elm/boolean "true" #elm/boolean "false" true?
      #elm/boolean "true" {:type "Null"} true?
      #elm/boolean "true" #elm/parameter-ref "b" false?

      #elm/boolean "false" #elm/boolean "true" true?
      #elm/boolean "false" #elm/boolean "false" true?
      #elm/boolean "false" {:type "Null"} true?
      #elm/boolean "false" #elm/parameter-ref "b" false?

      {:type "Null"} #elm/boolean "true" true?
      {:type "Null"} #elm/boolean "false" true?
      {:type "Null"} {:type "Null"} true?
      {:type "Null"} #elm/parameter-ref "b" true?

      #elm/parameter-ref "a" #elm/boolean "true" false?
      #elm/parameter-ref "a" #elm/boolean "false" false?
      #elm/parameter-ref "a" {:type "Null"} true?
      #elm/parameter-ref "a" #elm/parameter-ref "b" false?))

  (ctu/testing-binary-op elm/xor)

  (ctu/testing-optimize elm/xor
    (testing "with one null operand"
      [#ctu/optimizeable "x" {:type "Null"}]
      [{:type "Null"} #ctu/optimizeable "x"]
      nil)

    (testing "with two null operands"
      [{:type "Null"} {:type "Null"}]
      nil)

    (testing "with one operand optimizing to true"
      [#ctu/optimize-to true #ctu/optimizeable "x"]
      [#ctu/optimizeable "x" #ctu/optimize-to true]
      '(not (optimized "x")))

    (testing "with one operand optimizing to false"
      [#ctu/optimize-to false #ctu/optimizeable "x"]
      [#ctu/optimizeable "x" #ctu/optimize-to false]
      '(optimized "x"))

    (testing "with both operands optimizing to true or false"
      [#ctu/optimize-to true #ctu/optimize-to true]
      [#ctu/optimize-to false #ctu/optimize-to false]
      false)

    (testing "with one operand optimizing to true and the other to false"
      [#ctu/optimize-to true #ctu/optimize-to false]
      [#ctu/optimize-to false #ctu/optimize-to true]
      true)

    (testing "with at least one operand optimizing to nil"
      [#ctu/optimize-to nil #ctu/optimizeable "x"]
      [#ctu/optimizeable "x" #ctu/optimize-to nil]
      [#ctu/optimize-to nil #ctu/optimize-to true]
      [#ctu/optimize-to true #ctu/optimize-to nil]
      [#ctu/optimize-to nil #ctu/optimize-to false]
      [#ctu/optimize-to false #ctu/optimize-to nil]
      [#ctu/optimize-to nil #ctu/optimize-to nil]
      nil)))
