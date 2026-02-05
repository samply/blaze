(ns blaze.elm.compiler.test-util
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler-spec]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.core-spec]
   [blaze.elm.compiler.macros :refer [reify-expr]]
   [blaze.elm.expression.cache :as ec]
   [blaze.elm.literal :as elm]
   [blaze.elm.literal-spec]
   [blaze.elm.resource :as cr]
   [blaze.elm.spec :as elm-spec]
   [blaze.fhir.spec.type.system :as system]
   [blaze.module.test-util :refer [with-system]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :refer [is testing]]
   [juxt.iota :refer [given]])
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
  {:eval-context "Patient"
   :library
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
      {:name "a,b"}
      {:name "a,,b"}
      {:name "A"}
      {:name "2019"}
      {:name "2020"}
      {:name "2021"}
      {:name "2022"}
      {:name "12:54:00"}
      {:name "2020-01-02T03:04:05.006Z"}
      {:name "[1]"}
      {:name "[1 2]"}
      {:name "coding"}]}}})

(def dynamic-eval-ctx
  {:parameters
   {"true" true "false" false "nil" nil "-1" -1 "1" 1 "2" 2 "3" 3 "4" 4
    "empty-string" "" "x" "x" "y" "y" "z" "z"
    "a" "a" "ab" "ab" "b" "b" "ba" "ba" "a,b" "a,b" "a,,b" "a,,b" "A" "A"
    "2019" #system/date"2019"
    "2020" #system/date"2020"
    "2021" #system/date"2021"
    "2022" #system/date"2022"
    "12:54:00" #system/time"12:54:00"
    "2020-01-02T03:04:05.006Z" (system/date-time 2020 1 2 3 4 5 6 ZoneOffset/UTC)
    "[1]" [1] "[1 2]" [1 2]
    "coding" #fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"}}
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

(defmacro testing-constant-attach-cache [expr]
  `(testing "attach-cache"
     (is (= [~expr] (st/with-instrument-disabled (c/attach-cache ~expr ::cache))))))

(defmacro testing-constant-patient-count [expr]
  `(testing "patient count"
     (is (nil? (core/-patient-count ~expr)))))

(defmacro testing-constant-resolve-refs [expr]
  `(testing "resolve expression references"
     (is (= ~expr (c/resolve-refs ~expr {})))))

(defmacro testing-constant-resolve-params [expr]
  `(testing "resolve parameters"
     (is (= ~expr (c/resolve-params ~expr {})))))

(defmacro testing-constant-optimize [expr]
  `(testing "optimize"
     (is (= ~expr (st/with-instrument-disabled (c/optimize ~expr nil))))))

(defmacro testing-constant-eval [expr]
  `(testing "eval"
     (is (= ~expr (core/-eval ~expr {} nil nil)))))

(defmacro testing-constant [expr]
  `(do
     (testing "expression is static"
       (is (true? (core/-static ~expr))))

     (testing-constant-attach-cache ~expr)

     (testing-constant-patient-count ~expr)

     (testing-constant-resolve-refs ~expr)

     (testing-constant-resolve-params ~expr)

     (testing-constant-optimize ~expr)

     (testing-constant-eval ~expr)))

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

(defmacro testing-unary-attach-cache
  "Works with unary and aggregate operators."
  [elm-constructor]
  `(testing "attach cache"
     (with-redefs [ec/get #(do (assert (= ::cache %1)) (c/form %2))]
       (with-system [{node# :blaze.db/node} api-stub/mem-node-config]
         (let [elm# (~elm-constructor #elm/exists #elm/retrieve{:type "Observation"})
               ctx# {:node node# :eval-context "Patient"}
               expr# (c/compile ctx# elm#)]
           (given (st/with-instrument-disabled (c/attach-cache expr# ::cache))
             count := 2
             [0] := expr#
             [1 count] := 1
             [1 0] := '(~'exists (~'retrieve "Observation"))))))))

(defmacro testing-unary-precision-attach-cache
  [elm-constructor & precisions]
  `(testing "attach cache"
     (with-redefs [ec/get #(do (assert (= ::cache %1)) (c/form %2))]
       (with-system [{node# :blaze.db/node} api-stub/mem-node-config]
         (doseq [precision# ~(vec precisions)]
           (let [elm# (~elm-constructor [#elm/exists #elm/retrieve{:type "Observation"} precision#])
                 ctx# {:node node# :eval-context "Patient"}
                 expr# (c/compile ctx# elm#)]
             (given (st/with-instrument-disabled (c/attach-cache expr# ::cache))
               count := 2
               [0] := expr#
               [1 count] := 1
               [1 0] := '(~'exists (~'retrieve "Observation")))))))))

(defmacro testing-unary-patient-count [elm-constructor]
  `(testing "patient count"
     (let [expr# (dynamic-compile (~elm-constructor #elm/parameter-ref "x"))]
       (is (nil? (core/-patient-count expr#))))))

(defmacro testing-unary-precision-patient-count [elm-constructor & precisions]
  `(testing "patient count"
     (doseq [precision# ~(vec precisions)]
       (let [expr# (dynamic-compile (~elm-constructor [#elm/parameter-ref "x"
                                                       precision#]))]
         (is (nil? (core/-patient-count expr#)))))))

(defmacro testing-unary-resolve-refs
  "Works with unary and aggregate operators."
  [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "resolve expression references"
       (let [elm# (~elm-constructor #elm/expression-ref "x")
             expr-def# {:type "ExpressionDef" :name "x" :expression "y"
                        :context "Unfiltered"}
             ctx# {:library {:statements {:def [expr-def#]}}}
             expr# (c/resolve-refs (c/compile ctx# elm#) {"x" expr-def#})]
         (has-form expr# '(~form-name "y"))))))

(defmacro testing-unary-precision-resolve-refs
  "Works with unary and aggregate operators."
  [elm-constructor & precisions]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "resolve expression references"
       (doseq [precision# ~(vec precisions)]
         (let [elm# (~elm-constructor [#elm/expression-ref "x" precision#])
               expr-def# {:type "ExpressionDef" :name "x" :expression "y"
                          :context "Unfiltered"}
               ctx# {:library {:statements {:def [expr-def#]}}}
               expr# (c/resolve-refs (c/compile ctx# elm#) {"x" expr-def#})]
           (has-form expr# (list '~form-name "y" precision#)))))))

(defmacro testing-unary-resolve-params
  "Works with unary and aggregate operators."
  [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "resolve parameters"
       (let [elm# (~elm-constructor #elm/parameter-ref "x")
             ctx# {:library {:parameters {:def [{:name "x"}]}}}
             expr# (c/resolve-params (c/compile ctx# elm#) {"x" "y"})]
         (has-form expr# '(~form-name "y"))))))

(defn- optimized-expr [id]
  (reify-expr core/Expression
    (-form [_]
      (list 'optimized id))))

(defn- optimizeable-expr [id]
  (reify-expr core/Expression
    (-optimize [_ _]
      (optimized-expr id))
    (-form [_]
      (list 'optimizeable id))))

(s/def ::id
  string?)

(defmethod elm-spec/expression :elm.spec.type/optimizeable [_]
  (s/keys :req-un [::id]))

(defmethod core/compile* :elm.compiler.type/optimizeable
  [_ {:keys [id]}]
  (optimizeable-expr id))

(defn optimizeable [id]
  {:type "Optimizeable" :id id})

(s/def ::value
  any?)

(defmethod elm-spec/expression :elm.spec.type/optimize-to [_]
  (s/keys :req-un [::value]))

(defmethod core/compile* :elm.compiler.type/optimize-to
  [_ {:keys [value]}]
  (reify-expr core/Expression
    (-optimize [_ _]
      value)
    (-form [_]
      (list 'optimize-to value))))

(defn optimize-to [value]
  {:type "OptimizeTo" :value value})

(defmacro testing-unary-optimize [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "optimize"
       (let [elm# (~elm-constructor #ctu/optimizeable "x")
             expr# (st/with-instrument-disabled (c/optimize (c/compile {} elm#) nil))]
         (has-form expr# (list '~form-name (list '~'optimized "x")))))))

(defmacro testing-unary-precision-resolve-params
  "Works with unary and aggregate operators."
  [elm-constructor & precisions]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "resolve parameters"
       (doseq [precision# ~(vec precisions)]
         (let [elm# (~elm-constructor [#elm/parameter-ref "x" precision#])
               ctx# {:library {:parameters {:def [{:name "x"}]}}}
               expr# (c/resolve-params (c/compile ctx# elm#) {"x" "y"})]
           (has-form expr# (list '~form-name "y" precision#)))))))

(defmacro testing-unary-precision-optimize
  "Works with unary and aggregate operators."
  [elm-constructor & precisions]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "optimize"
       (doseq [precision# ~(vec precisions)]
         (let [elm# (~elm-constructor [#ctu/optimizeable "x" precision#])
               expr# (st/with-instrument-disabled (c/optimize (c/compile {} elm#) nil))]
           (has-form expr# (list '~form-name (list '~'optimized "x") precision#)))))))

(defmacro testing-unary-equals-hash-code
  [elm-constructor]
  `(testing "equals/hashCode"
     (let [elm# (~elm-constructor #elm/parameter-ref "x")
           ctx# {:library {:parameters {:def [{:name "x"}]}}}
           expr-1# (c/compile ctx# elm#)
           expr-2# (c/compile ctx# elm#)]
       (is (and (.equals ^Object expr-1# expr-2#)
                (= (.hashCode ^Object expr-1#)
                   (.hashCode ^Object expr-2#)))))))

(defmacro testing-unary-precision-equals-hash-code
  [elm-constructor & precisions]
  `(testing "equals/hashCode"
     (doseq [precision# ~(vec precisions)]
       (let [elm# (~elm-constructor [#elm/parameter-ref "x" precision#])
             ctx# {:library {:parameters {:def [{:name "x"}]}}}
             expr-1# (c/compile ctx# elm#)
             expr-2# (c/compile ctx# elm#)]
         (is (and (.equals ^Object expr-1# expr-2#)
                  (= (.hashCode ^Object expr-1#)
                     (.hashCode ^Object expr-2#))))))))

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

(defmacro testing-binary-equals-hash-code
  [elm-constructor]
  `(testing "equals/hashCode"
     (let [elm# (~elm-constructor [#elm/parameter-ref "x" #elm/parameter-ref "y"])
           ctx# {:library {:parameters {:def [{:name "x"} {:name "y"}]}}}
           expr-1# (c/compile ctx# elm#)
           expr-2# (c/compile ctx# elm#)]
       (is (and (.equals ^Object expr-1# expr-2#)
                (= (.hashCode ^Object expr-1#)
                   (.hashCode ^Object expr-2#)))))))

(defmacro testing-binary-precision-equals-hash-code
  ([elm-constructor]
   `(testing-binary-precision-equals-hash-code ~elm-constructor "year" "month"))
  ([elm-constructor & precisions]
   `(testing "equals/hashCode"
      (doseq [precision# ~(vec precisions)]
        (let [elm# (~elm-constructor [#elm/parameter-ref "x" #elm/parameter-ref "y" precision#])
              ctx# {:library {:parameters {:def [{:name "x"} {:name "y"}]}}}
              expr-1# (c/compile ctx# elm#)
              expr-2# (c/compile ctx# elm#)]
          (is (and (.equals ^Object expr-1# expr-2#)
                   (= (.hashCode ^Object expr-1#)
                      (.hashCode ^Object expr-2#)))))))))

(defmacro testing-ternary-equals-hash-code
  [elm-constructor]
  `(testing "equals/hashCode"
     (let [elm# (~elm-constructor [#elm/parameter-ref "x" #elm/parameter-ref "y"
                                   #elm/parameter-ref "z"])
           ctx# {:library {:parameters {:def [{:name "x"} {:name "y"} {:name "z"}]}}}
           expr-1# (c/compile ctx# elm#)
           expr-2# (c/compile ctx# elm#)]
       (is (and (.equals ^Object expr-1# expr-2#)
                (= (.hashCode ^Object expr-1#)
                   (.hashCode ^Object expr-2#)))))))

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

(defmacro testing-binary-dynamic [elm-constructor]
  `(testing "expression is dynamic"
     (is (false? (core/-static (dynamic-compile (~elm-constructor
                                                 [#elm/parameter-ref "x"
                                                  #elm/parameter-ref "y"])))))))

(defn mock-cache-get [cache expr]
  (assert (= ::cache cache))
  (when (= '(exists (retrieve "Observation")) (c/form expr))
    (c/form expr)))

(defmacro testing-binary-attach-cache
  [elm-constructor]
  `(testing "attach cache"
     (with-redefs [ec/get mock-cache-get]
       (with-system [{node# :blaze.db/node} api-stub/mem-node-config]
         (let [elm# (~elm-constructor
                     [#elm/exists #elm/retrieve{:type "Observation"}
                      #elm/exists #elm/retrieve{:type "Condition"}])
               ctx# {:node node# :eval-context "Patient"}
               expr# (c/compile ctx# elm#)]
           (given (st/with-instrument-disabled (c/attach-cache expr# ::cache))
             count := 2
             [0] := expr#
             [1 count] := 2
             [1 0] := '(~'exists (~'retrieve "Observation"))
             [1 1] := (ba/unavailable "No Bloom filter available.")))))))

(defmacro testing-ternary-attach-cache
  [elm-constructor]
  `(testing "attach cache"
     (with-redefs [ec/get mock-cache-get]
       (with-system [{node# :blaze.db/node} api-stub/mem-node-config]
         (let [elm# (~elm-constructor
                     [#elm/exists #elm/retrieve{:type "Observation"}
                      #elm/exists #elm/retrieve{:type "Condition"}
                      #elm/exists #elm/retrieve{:type "Specimen"}])
               ctx# {:node node# :eval-context "Patient"}
               expr# (c/compile ctx# elm#)]
           (given (st/with-instrument-disabled (c/attach-cache expr# ::cache))
             count := 2
             [0] := expr#
             [1 count] := 3
             [1 0] := '(~'exists (~'retrieve "Observation"))
             [1 1] := (ba/unavailable "No Bloom filter available.")
             [1 2] := (ba/unavailable "No Bloom filter available.")))))))

(defmacro testing-binary-precision-attach-cache
  ([elm-constructor]
   `(testing-binary-precision-attach-cache ~elm-constructor "year" "month"))
  ([elm-constructor & precisions]
   `(testing "attach cache"
      (with-redefs [ec/get #(do (assert (= ::cache %1)) (c/form %2))]
        (with-system [{node# :blaze.db/node} api-stub/mem-node-config]
          (doseq [precision# ~(vec precisions)]
            (let [elm# (~elm-constructor
                        [#elm/exists #elm/retrieve{:type "Observation"}
                         #elm/exists #elm/retrieve{:type "Condition"}
                         precision#])
                  ctx# {:node node# :eval-context "Patient"}
                  expr# (c/compile ctx# elm#)]
              (given (st/with-instrument-disabled (c/attach-cache expr# ::cache))
                count := 2
                [0] := expr#
                [1 count] := 2
                [1 0] := '(~'exists (~'retrieve "Observation"))
                [1 1] := '(~'exists (~'retrieve "Condition"))))))))))

(defmacro testing-binary-patient-count [elm-constructor]
  `(testing "patient count"
     (is (nil? (core/-patient-count (dynamic-compile (~elm-constructor
                                                      [#elm/parameter-ref "x"
                                                       #elm/parameter-ref "y"])))))))

(defmacro testing-binary-precision-patient-count
  ([elm-constructor]
   `(testing-binary-precision-patient-count ~elm-constructor "year" "month"))
  ([elm-constructor & precisions]
   `(testing "patient count"
      (doseq [precision# ~(vec precisions)]
        (is (nil? (core/-patient-count (dynamic-compile (~elm-constructor
                                                         [#elm/parameter-ref "x"
                                                          #elm/parameter-ref "y"
                                                          precision#])))))))))

(defmacro testing-ternary-patient-count [elm-constructor]
  `(testing "patient count"
     (is (nil? (core/-patient-count (dynamic-compile (~elm-constructor
                                                      [#elm/parameter-ref "x"
                                                       #elm/parameter-ref "y"
                                                       #elm/parameter-ref "z"])))))))

(defmacro testing-binary-resolve-refs
  [elm-constructor & [form-name]]
  (let [form-name (or (some-> form-name symbol) (symbol (name elm-constructor)))]
    `(testing "resolve expression references"
       (let [elm# (~elm-constructor
                   [#elm/expression-ref "x"
                    #elm/expression-ref "y"])
             expr-defs# [{:type "ExpressionDef" :name "x" :expression "a"
                          :context "Unfiltered"}
                         {:type "ExpressionDef" :name "y" :expression "b"
                          :context "Unfiltered"}]
             ctx# {:library {:statements {:def expr-defs#}}}
             expr# (c/resolve-refs (c/compile ctx# elm#) (zipmap ["x" "y"] expr-defs#))]
         (has-form expr# '(~form-name "a" "b"))))))

(defmacro testing-ternary-resolve-refs [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "resolve expression references"
       (let [elm# (~elm-constructor
                   [#elm/expression-ref "x"
                    #elm/expression-ref "y"
                    #elm/expression-ref "z"])
             expr-defs# [{:type "ExpressionDef" :name "x" :expression "a"
                          :context "Unfiltered"}
                         {:type "ExpressionDef" :name "y" :expression "b"
                          :context "Unfiltered"}
                         {:type "ExpressionDef" :name "z" :expression "c"
                          :context "Unfiltered"}]
             ctx# {:library {:statements {:def expr-defs#}}}
             expr# (c/resolve-refs (c/compile ctx# elm#) (zipmap ["x" "y" "z"] expr-defs#))]
         (has-form expr# '(~form-name "a" "b" "c"))))))

(defmacro testing-binary-resolve-params [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "resolve parameters"
       (let [elm# (~elm-constructor [#elm/parameter-ref "x" #elm/parameter-ref "y"])
             ctx# {:library {:parameters {:def [{:name "x"} {:name "y"}]}}}
             expr# (c/resolve-params (c/compile ctx# elm#) {"x" "a" "y" "b"})]
         (has-form expr# '(~form-name "a" "b"))))))

(defmacro testing-binary-optimize [elm-constructor & [form-name]]
  (let [form-name (or (some-> form-name symbol) (symbol (name elm-constructor)))]
    `(testing "optimize"
       (let [elm# (~elm-constructor [#ctu/optimizeable "a"
                                     #ctu/optimizeable "b"])
             expr# (st/with-instrument-disabled (c/optimize (c/compile {} elm#) nil))]
         (has-form expr# (list (quote ~form-name) (list (quote ~'optimized) "a") (list (quote ~'optimized) "b")))))))

(defmacro testing-ternary-resolve-params [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "resolve parameters"
       (let [elm# (~elm-constructor [#elm/parameter-ref "x" #elm/parameter-ref "y"
                                     #elm/parameter-ref "z"])
             ctx# {:library {:parameters {:def [{:name "x"} {:name "y"} {:name "z"}]}}}
             expr# (c/resolve-params (c/compile ctx# elm#) {"x" "a" "y" "b" "z" "c"})]
         (has-form expr# '(~form-name "a" "b" "c"))))))

(defmacro testing-ternary-optimize [elm-constructor]
  (let [form-name (symbol (name elm-constructor))]
    `(testing "optimize"
       (let [elm# (~elm-constructor [#ctu/optimizeable "x"
                                     #ctu/optimizeable "y"
                                     #ctu/optimizeable "z"])
             expr# (st/with-instrument-disabled (c/optimize (c/compile {} elm#) nil))]
         (has-form expr# (list (quote ~form-name)
                               (list (quote ~'optimized) "x")
                               (list (quote ~'optimized) "y")
                               (list (quote ~'optimized) "z")))))))

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

(defmacro testing-binary-precision-resolve-refs
  ([elm-constructor]
   `(testing-binary-precision-resolve-refs ~elm-constructor "year" "month"))
  ([elm-constructor & precisions]
   (let [form-name (symbol (name elm-constructor))]
     `(testing "resolve expression references"
        (doseq [precision# ~(vec precisions)]
          (let [elm# (~elm-constructor
                      [#elm/expression-ref "x"
                       #elm/expression-ref "y"
                       precision#])
                expr-def-x# {:type "ExpressionDef" :name "x" :expression "a"
                             :context "Unfiltered"}
                expr-def-y# {:type "ExpressionDef" :name "y" :expression "b"
                             :context "Unfiltered"}
                ctx# {:library {:statements {:def [expr-def-x# expr-def-y#]}}}
                expr# (c/resolve-refs (c/compile ctx# elm#) {"x" expr-def-x#
                                                             "y" expr-def-y#})]
            (has-form expr# (list (quote ~form-name) "a" "b" precision#))))))))

(defmacro testing-binary-precision-resolve-params
  ([elm-constructor]
   `(testing-binary-precision-resolve-params ~elm-constructor "year" "month"))
  ([elm-constructor & precisions]
   (let [form-name (symbol (name elm-constructor))]
     `(testing "resolve parameters"
        (doseq [precision# ~(vec precisions)]
          (let [elm# (~elm-constructor
                      [#elm/parameter-ref "x"
                       #elm/parameter-ref "y"
                       precision#])
                ctx# {:library {:parameters {:def [{:name "x"} {:name "y"}]}}}
                expr# (c/resolve-params (c/compile ctx# elm#) {"x" "a" "y" "b"})]
            (has-form expr# (list (quote ~form-name) "a" "b" precision#))))))))

(defmacro testing-binary-precision-optimize
  ([elm-constructor]
   `(testing-binary-precision-optimize ~elm-constructor "year" "month"))
  ([elm-constructor & precisions]
   (let [form-name (symbol (name elm-constructor))]
     `(testing "optimize"
        (doseq [precision# ~(vec precisions)]
          (let [elm# (~elm-constructor
                      [#ctu/optimizeable "x"
                       #ctu/optimizeable "y"
                       precision#])
                expr# (st/with-instrument-disabled (c/optimize (c/compile {} elm#) nil))]
            (has-form expr# (list '~form-name (list '~'optimized "x") (list '~'optimized "y") precision#))))))))

(defmacro testing-ternary-dynamic [elm-constructor]
  `(testing "expression is dynamic"
     (is (false? (core/-static (dynamic-compile (~elm-constructor
                                                 [#elm/parameter-ref "x"
                                                  #elm/parameter-ref "y"
                                                  #elm/parameter-ref "z"])))))))

(defmacro testing-unary-op [elm-constructor]
  `(do
     (testing-unary-dynamic ~elm-constructor)

     (testing-unary-attach-cache ~elm-constructor)

     (testing-unary-patient-count ~elm-constructor)

     (testing-unary-resolve-refs ~elm-constructor)

     (testing-unary-resolve-params ~elm-constructor)

     (testing-unary-optimize ~elm-constructor)

     (testing-unary-equals-hash-code ~elm-constructor)

     (testing-unary-form ~elm-constructor)))

(defmacro testing-unary-precision-op [elm-constructor & precisions]
  `(do
     (testing-unary-precision-dynamic ~elm-constructor ~@precisions)

     (testing-unary-precision-attach-cache ~elm-constructor ~@precisions)

     (testing-unary-precision-patient-count ~elm-constructor ~@precisions)

     (testing-unary-precision-resolve-refs ~elm-constructor ~@precisions)

     (testing-unary-precision-resolve-params ~elm-constructor ~@precisions)

     (testing-unary-precision-optimize ~elm-constructor ~@precisions)

     (testing-unary-precision-equals-hash-code ~elm-constructor ~@precisions)

     (testing-unary-precision-form ~elm-constructor ~@precisions)))

(defmacro testing-binary-op [elm-constructor]
  `(do
     (testing-binary-dynamic ~elm-constructor)

     (testing-binary-attach-cache ~elm-constructor)

     (testing-binary-patient-count ~elm-constructor)

     (testing-binary-resolve-refs ~elm-constructor)

     (testing-binary-resolve-params ~elm-constructor)

     (testing-binary-optimize ~elm-constructor)

     (testing-binary-equals-hash-code ~elm-constructor)

     (testing-binary-form ~elm-constructor)))

(defmacro testing-binary-precision-op [elm-constructor]
  `(do
     (testing-binary-dynamic ~elm-constructor)

     (testing-binary-precision-dynamic ~elm-constructor)

     (testing-binary-attach-cache ~elm-constructor)

     (testing-binary-precision-attach-cache ~elm-constructor)

     (testing-binary-patient-count ~elm-constructor)

     (testing-binary-precision-patient-count ~elm-constructor)

     (testing-binary-resolve-refs ~elm-constructor)

     (testing-binary-precision-resolve-refs ~elm-constructor)

     (testing-binary-resolve-params ~elm-constructor)

     (testing-binary-optimize ~elm-constructor)

     (testing-binary-precision-resolve-params ~elm-constructor)

     (testing-binary-precision-optimize ~elm-constructor)

     (testing-binary-equals-hash-code ~elm-constructor)

     (testing-binary-precision-equals-hash-code ~elm-constructor)

     (testing-binary-form ~elm-constructor)

     (testing-binary-precision-form ~elm-constructor)))

(defmacro testing-binary-precision-only-op [elm-constructor & precisions]
  `(do
     (testing-binary-precision-dynamic ~elm-constructor ~@precisions)

     (testing-binary-precision-attach-cache ~elm-constructor ~@precisions)

     (testing-binary-precision-patient-count ~elm-constructor ~@precisions)

     (testing-binary-precision-resolve-refs ~elm-constructor ~@precisions)

     (testing-binary-precision-resolve-params ~elm-constructor ~@precisions)

     (testing-binary-precision-optimize ~elm-constructor ~@precisions)

     (testing-binary-precision-equals-hash-code ~elm-constructor ~@precisions)

     (testing-binary-precision-form ~elm-constructor ~@precisions)))

(defmacro testing-ternary-op [elm-constructor]
  `(do
     (testing-ternary-dynamic ~elm-constructor)

     (testing-ternary-attach-cache ~elm-constructor)

     (testing-ternary-patient-count ~elm-constructor)

     (testing-ternary-resolve-refs ~elm-constructor)

     (testing-ternary-resolve-params ~elm-constructor)

     (testing-ternary-optimize ~elm-constructor)

     (testing-ternary-equals-hash-code ~elm-constructor)

     (testing-ternary-form ~elm-constructor)))

(defmacro testing-equals-hash-code [elm]
  `(testing "equals/hashCode"
     (let [expr-1# (dynamic-compile ~elm)
           expr-2# (dynamic-compile ~elm)]
       (is (= 1 (count (set [expr-1# expr-2#])))))))

(defn resource [db type id]
  (cr/mk-resource db (d/resource-handle db type id)))

(defmacro testing-optimize
  "Generates testing forms for the expression with `elm-constructor`.

  Each `test-case` is a testing form with a description followed by one or more
  vectors of elm operators followed by one expected form."
  [elm-constructor & test-cases]
  `(testing "optimize"
     ~@(for [[_ test-name & more] test-cases
             :let [ops (butlast more)
                   expected (last more)]]
         `(testing ~test-name
            ~@(for [op ops]
                `(let [elm# (~elm-constructor ~op)
                       expr# (st/with-instrument-disabled (c/optimize (c/compile {} elm#) nil))]
                   (has-form expr# ~expected)))))))
