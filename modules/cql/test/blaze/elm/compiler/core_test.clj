(ns blaze.elm.compiler.core-test
  (:require
   [blaze.coll.core :as coll]
   [blaze.elm.compiler-spec]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :refer [deftest is testing]]))

(set! *warn-on-reflection* true)
(st/instrument)

(deftest attach-cache-test
  (testing "nil expressions will be returned unchanged"
    (is (nil? (first ((first (core/-attach-cache nil nil)))))))

  (testing "expressions of type Object will be returned unchanged"
    (let [expression (Object.)]
      (is (identical? expression (first ((first (core/-attach-cache expression nil))))))))

  (testing "expressions of type IReduceInit will be returned unchanged"
    (let [expression (coll/eduction identity [])]
      (is (identical? expression (first ((first (core/-attach-cache expression nil)))))))))

(deftest resolve-refs-test
  (testing "nil expressions will be returned unchanged"
    (is (nil? (core/-resolve-refs nil {}))))

  (testing "expressions of type Object will be returned unchanged"
    (let [expression (Object.)]
      (is (identical? expression (core/-resolve-refs expression {})))))

  (testing "expressions of type IReduceInit will be returned unchanged"
    (let [expression (coll/eduction identity [])]
      (is (identical? expression (core/-resolve-refs expression {}))))))

(deftest resolve-params-test
  (testing "nil expressions will be returned unchanged"
    (is (nil? (core/-resolve-params nil {}))))

  (testing "expressions of type Object will be returned unchanged"
    (let [expression (Object.)]
      (is (identical? expression (core/-resolve-params expression {})))))

  (testing "expressions of type IReduceInit will be returned unchanged"
    (let [expression (coll/eduction identity [])]
      (is (identical? expression (core/-resolve-params expression {}))))))

(deftest optimize-test
  (testing "nil expressions will be returned unchanged"
    (is (nil? (core/-optimize nil nil))))

  (testing "expressions of type Object will be returned unchanged"
    (let [expression (Object.)]
      (is (identical? expression (core/-optimize expression nil)))))

  (testing "expressions of type IReduceInit will be returned unchanged"
    (let [expression (coll/eduction identity [])]
      (is (identical? expression (core/-optimize expression nil))))))

(deftest function-call-form-test
  (testing "arity 0"
    (is (= '((fn [] 1)) (core/function-call-form "f" [] 1 []))))

  (testing "arity 2"
    (is (= '((fn ["x" "y"] (add (operand-ref "x") (operand-ref "y"))) 1 2)
           (core/function-call-form "f" ["x" "y"] '(add (operand-ref "x") (operand-ref "y")) [1 2]))))

  (testing "the form doesn't depend on the function name"
    (is (= (core/function-call-form "f" ["x"] '(operand-ref "x") [1])
           (core/function-call-form "g" ["x"] '(operand-ref "x") [1]))))

  (testing "the form is a list"
    (is (list? (core/function-call-form "f" [] 1 [])))))

(defn- form-expr [form]
  (reify-expr core/Expression
    (-form [_]
      form)))

(deftest display-form-test
  (testing "function calls are displayed with their name"
    (is (= '(call "f" 1)
           (core/display-form (form-expr (core/function-call-form "f" ["x"] '(operand-ref "x") [1]))))))

  (testing "function calls in operands are displayed with their name"
    (let [g (core/function-call-form "g" [] 1 [])]
      (is (= '(call "f" (call "g"))
             (core/display-form (form-expr (core/function-call-form "f" ["x"] '(operand-ref "x") [g])))))))

  (testing "function calls inside lists, vectors and maps are displayed with their name"
    (let [f (core/function-call-form "f" [] 1 [])]
      (is (= '(exists (call "f"))
             (core/display-form (form-expr (list 'exists f)))))
      (is (= '[(call "f")]
             (core/display-form (form-expr [f]))))
      (is (= '{"a" (call "f")}
             (core/display-form (form-expr {"a" f}))))))

  (testing "anonymous function applications without name stay unchanged"
    (is (= '((fn [] 1)) (core/display-form (form-expr '((fn [] 1)))))))

  (testing "other forms stay unchanged"
    (is (= '(add 1 2) (core/display-form (form-expr '(add 1 2)))))
    (is (= 1 (core/display-form 1)))
    (is (= 'nil (core/display-form nil)))))
