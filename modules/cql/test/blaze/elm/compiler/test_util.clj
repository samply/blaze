(ns blaze.elm.compiler.test-util
  (:require
   [blaze.db.api :as d]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.external-data :as ed]
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [blaze.elm.spec]
   [blaze.fhir.spec.type.system :as system]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :refer [is testing]])
  (:import
   [java.time OffsetDateTime ZoneOffset]))

(set! *warn-on-reflection* true)

(defn instrument-compile []
  (st/instrument
   `c/compile
   {:spec
    {`c/compile
     (s/fspec
      :args (s/cat :context map? :expression :elm/expression))}}))

(defn code
  ([system code]
   (elm/instance
    ["{urn:hl7-org:elm-types:r1}Code"
     {"system" (elm/string system) "code" (elm/string code)}]))
  ([system version code]
   (elm/instance
    ["{urn:hl7-org:elm-types:r1}Code"
     {"system" (elm/string system)
      "version" (elm/string version)
      "code" (elm/string code)}])))

(def patient-retrieve-elm
  {:type "Retrieve" :dataType "{http://hl7.org/fhir}Patient"})

(def now (OffsetDateTime/now (ZoneOffset/ofHours 0)))

(def dynamic-compile-ctx
  {:library
   {:parameters
    {:def
     [{:name "true"}
      {:name "false"}
      {:name "nil"}
      {:name "-1"}
      {:name "1"}
      {:name "2"}
      {:name "3"}
      {:name "4"}
      {:name "empty-string"}
      {:name "x"}
      {:name "y"}
      {:name "z"}
      {:name "a"}
      {:name "ab"}
      {:name "b"}
      {:name "ba"}
      {:name "A"}
      {:name "2019"}
      {:name "2020"}
      {:name "2021"}
      {:name "2022"}
      {:name "12:54:00"}
      {:name "2020-01-02T03:04:05.006Z"}
      {:name "[1]"}
      {:name "[1 2]"}]}}})

(def dynamic-eval-ctx
  {:parameters
   {"true" true "false" false "nil" nil "-1" -1 "1" 1 "2" 2 "3" 3 "4" 4
    "empty-string" "" "x" "x" "y" "y" "z" "z" "a" "a" "ab" "ab" "b" "b" "ba" "ba" "A" "A"
    "2019" (system/date 2019)
    "2020" (system/date 2020)
    "2021" (system/date 2021)
    "2022" (system/date 2022)
    "12:54:00" (system/time 12 54 00)
    "2020-01-02T03:04:05.006Z" (system/date-time 2020 1 2 3 4 5 6 ZoneOffset/UTC)
    "[1]" [1] "[1 2]" [1 2]}
   :now now})

(defn dynamic-compile [elm]
  (c/compile dynamic-compile-ctx elm))

(defn dynamic-compile-eval [elm]
  (core/-eval (dynamic-compile elm) dynamic-eval-ctx nil nil))

(defn binary-operand [type]
  {:type type :operand [{:type "Null"} {:type "Null"}]})

(defmacro unsupported-binary-operand [type]
  `(is (~'thrown-with-msg? Exception #"Unsupported" (c/compile {} (binary-operand ~type)))))

(defn unary-operand [type]
  {:type type :operand {:type "Null"}})

(defmacro unsupported-unary-operand [type]
  `(is (~'thrown-with-msg? Exception #"Unsupported" (c/compile {} (unary-operand ~type)))))

(defmacro testing-unary-static-null [elm-constructor]
  `(testing "Static Null"
     (is (nil? (c/compile {} (~elm-constructor {:type "Null"}))))))

(defmacro testing-unary-dynamic-null [elm-constructor]
  `(testing "Dynamic Null"
     (let [elm# (~elm-constructor #elm/parameter-ref "nil")]
       (is (nil? (dynamic-compile-eval elm#))))))

(defmacro testing-unary-null [elm-constructor]
  `(do
     (testing-unary-static-null ~elm-constructor)
     (testing-unary-dynamic-null ~elm-constructor)))

(defmacro testing-binary-static-null [elm-constructor non-null-op-1 non-null-op-2]
  `(testing "Static Null"
     (is (nil? (c/compile {} (~elm-constructor [{:type "Null"} {:type "Null"}]))))
     (is (nil? (c/compile {} (~elm-constructor [~non-null-op-1 {:type "Null"}]))))
     (is (nil? (c/compile {} (~elm-constructor [{:type "Null"} ~non-null-op-2]))))))

(defmacro testing-binary-dynamic-null
  [elm-constructor non-null-op-1 non-null-op-2]
  `(testing "Dynamic Null"
     (let [elm# (~elm-constructor
                 [#elm/parameter-ref "nil"
                  #elm/parameter-ref "nil"])]
       (is (nil? (dynamic-compile-eval elm#))))
     (let [elm# (~elm-constructor
                 [~non-null-op-1
                  #elm/parameter-ref "nil"])]
       (is (nil? (dynamic-compile-eval elm#))))
     (let [elm# (~elm-constructor
                 [#elm/parameter-ref "nil"
                  ~non-null-op-2])]
       (is (nil? (dynamic-compile-eval elm#))))))

(defmacro testing-ternary-dynamic-null
  [elm-constructor non-null-op-1 non-null-op-2 non-null-op-3]
  `(testing "Dynamic Null"
     (let [elm# (~elm-constructor
                 [#elm/parameter-ref "nil"
                  ~non-null-op-2
                  ~non-null-op-3])]
       (is (nil? (dynamic-compile-eval elm#))))
     (let [elm# (~elm-constructor
                 [~non-null-op-1
                  #elm/parameter-ref "nil"
                  ~non-null-op-3])]
       (is (nil? (dynamic-compile-eval elm#))))
     (let [elm# (~elm-constructor
                 [~non-null-op-1
                  ~non-null-op-2
                  #elm/parameter-ref "nil"])]
       (is (nil? (dynamic-compile-eval elm#))))))

(defmacro testing-binary-null
  ([elm-constructor non-null-op]
   `(testing-binary-null ~elm-constructor ~non-null-op ~non-null-op))
  ([elm-constructor non-null-op-1 non-null-op-2]
   `(do
      (testing-binary-static-null ~elm-constructor ~non-null-op-1 ~non-null-op-2)
      (testing-binary-dynamic-null ~elm-constructor ~non-null-op-1 ~non-null-op-2))))

(defn compile-unop [constructor op-constructor op]
  (c/compile {} (constructor (op-constructor op))))

(defn compile-unop-precision [constructor op-constructor op precision]
  (c/compile {} (constructor [(op-constructor op) precision])))

(defn compile-binop
  ([constructor op-constructor op-1 op-2]
   (compile-binop constructor op-constructor op-constructor op-1 op-2))
  ([constructor op-constructor-1 op-constructor-2 op-1 op-2]
   (c/compile {} (constructor [(op-constructor-1 op-1) (op-constructor-2 op-2)]))))

(defn compile-binop-precision [constructor op-constructor op-1 op-2 precision]
  (c/compile {} (constructor [(op-constructor op-1) (op-constructor op-2) precision])))

(defmacro has-form [expr form]
  `(is (= ~form (core/-form ~expr))))

(defmacro testing-constant-form [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "form"
       (let [expr# (dynamic-compile ~elm-constructor)]
         (has-form expr# (quote ~form-name))))))

(defmacro testing-unary-form
  "Works with unary and aggregate operators."
  [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "form"
       (let [elm# (~elm-constructor #elm/parameter-ref "x")
             expr# (dynamic-compile elm#)]
         (has-form expr# '(~form-name (~'param-ref "x")))))))

(defmacro testing-unary-precision-form
  ([elm-constructor]
   `(testing-unary-precision-form ~elm-constructor "year" "month"))
  ([elm-constructor & precisions]
   (let [form-name (symbol (name elm-constructor))]
     `(testing "form"
        (doseq [precision# ~(vec precisions)]
          (let [elm# (~elm-constructor [#elm/parameter-ref "x" precision#])
                expr# (dynamic-compile elm#)]
            (has-form expr#
              (list '~form-name '(~'param-ref "x") precision#))))))))

(defmacro testing-binary-form [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "form"
       (let [elm# (~elm-constructor [#elm/parameter-ref "x"
                                     #elm/parameter-ref "y"])
             expr# (dynamic-compile elm#)]
         (has-form expr#
           (quote (~form-name (~'param-ref "x") (~'param-ref "y"))))))))

(defmacro testing-binary-precision-form
  ([elm-constructor]
   `(testing-binary-precision-form ~elm-constructor "year" "month"))
  ([elm-constructor & precisions]
   (let [form-name (symbol (name elm-constructor))]
     `(testing "form"
        (doseq [precision# ~(vec precisions)]
          (let [elm# (~elm-constructor [#elm/parameter-ref "x"
                                        #elm/parameter-ref "y" precision#])
                expr# (dynamic-compile elm#)]
            (has-form expr#
              (list '~form-name '(~'param-ref "x") '(~'param-ref "y") precision#))))))))

(defmacro testing-ternary-form [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "form"
       (let [elm# (~elm-constructor [#elm/parameter-ref "x"
                                     #elm/parameter-ref "y"
                                     #elm/parameter-ref "z"])
             expr# (dynamic-compile elm#)]
         (has-form expr#
           (quote (~form-name (~'param-ref "x") (~'param-ref "y") (~'param-ref "z"))))))))

(defn with-locator [constructor locator]
  (comp #(assoc % :locator locator) constructor))

(defmacro testing-constant-dynamic [elm-constructor]
  `(testing "expression is dynamic"
     (is (false? (core/-static (dynamic-compile ~elm-constructor))))))

(defmacro testing-unary-dynamic [elm-constructor]
  `(testing "expression is dynamic"
     (is (false? (core/-static (dynamic-compile (~elm-constructor
                                                 #elm/parameter-ref "x")))))))

(defmacro testing-unary-precision-dynamic
  [elm-constructor & precisions]
  `(testing "expression is dynamic"
     (doseq [precision# ~(vec precisions)]
       (is (false? (core/-static (dynamic-compile (~elm-constructor
                                                   [#elm/parameter-ref "x"
                                                    precision#]))))))))

(defmacro testing-binary-dynamic [elm-constructor]
  `(testing "expression is dynamic"
     (is (false? (core/-static (dynamic-compile (~elm-constructor
                                                 [#elm/parameter-ref "x"
                                                  #elm/parameter-ref "y"])))))))

(defmacro testing-binary-precision-dynamic
  ([elm-constructor]
   `(testing-binary-precision-dynamic ~elm-constructor "year" "month"))
  ([elm-constructor & precisions]
   `(testing "expression is dynamic"
      (doseq [precision# ~(vec precisions)]
        (is (false? (core/-static (dynamic-compile (~elm-constructor
                                                    [#elm/parameter-ref "x"
                                                     #elm/parameter-ref "y"
                                                     precision#])))))))))

(defmacro testing-ternary-dynamic [elm-constructor]
  `(testing "expression is dynamic"
     (is (false? (core/-static (dynamic-compile (~elm-constructor
                                                 [#elm/parameter-ref "x"
                                                  #elm/parameter-ref "y"
                                                  #elm/parameter-ref "z"])))))))

(defn resource [db type id]
  (ed/mk-resource db (d/resource-handle db type id)))

(defn eval-unfiltered [elm]
  (core/-eval (c/compile {:eval-context "Unfiltered"} elm) {} nil nil))
