(ns blaze.elm.compiler-test
  "Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.elm.compiler :refer [compile compile-with-equiv-clause]]
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.compiler.retrieve-spec]
    [blaze.elm.compiler.retrieve-test :as retrieve-test]
    [blaze.elm.compiler.query :as query]
    [blaze.elm.date-time :refer [local-time local-time? period]]
    [blaze.elm.decimal :as decimal]
    [blaze.elm.interval :refer [interval]]
    [blaze.elm.literal :as elm]
    [blaze.elm.quantity :refer [quantity]]
    [blaze.elm.quantity-spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [blaze.elm.code Code]
    [blaze.elm.compiler.retrieve WithRelatedContextQueryRetrieveExpression]
    [blaze.elm.date_time Period]
    [java.math BigDecimal]
    [java.time LocalDate LocalDateTime OffsetDateTime Year YearMonth
               ZoneOffset]
    [java.time.temporal Temporal]
    [javax.measure UnconvertibleException]
    [clojure.lang IPersistentCollection])
  (:refer-clojure :exclude [compile]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (st/instrument
    `compile
    {:spec
     {`compile
      (s/fspec
        :args (s/cat :context any? :expression :elm/expression))}})
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defmacro satisfies-prop [num-tests prop]
  `(let [result# (tc/quick-check ~num-tests ~prop)]
     (if (instance? Throwable (:result result#))
       (throw (:result result#))
       (if (true? (:result result#))
         (is :success)
         (is (clojure.pprint/pprint result#))))))


(def now (OffsetDateTime/now (ZoneOffset/ofHours 0)))


(defn- binary-operand [type]
  {:type type :operand [{:type "Null"} {:type "Null"}]})


(defmacro unsupported-binary-operand [type]
  `(is (~'thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand ~type)))))


(defn- unary-operand [type]
  {:type type :operand {:type "Null"}})


(defmacro unsupported-unary-operand [type]
  `(is (~'thrown-with-msg? Exception #"Unsupported" (compile {} (unary-operand ~type)))))


(def patient-retrieve-elm
  {:type "Retrieve" :dataType "{http://hl7.org/fhir}Patient"})


(defmacro testing-unary-static-null [elm-constructor]
  `(testing "Static Null"
     (is (nil? (compile {} (~elm-constructor {:type "Null"}))))))


(defmacro testing-unary-dynamic-null [elm-constructor]
  `(testing "Dynamic Null"
     (let [context# {:eval-context "Patient" :node (mem-node-with [])}
           elm# (~elm-constructor #elm/singleton-from patient-retrieve-elm)
           expr# (compile context# elm#)]
       (is (nil? (-eval expr# {} nil nil))))))


(defmacro testing-unary-null [elm-constructor]
  `(do
     (testing-unary-static-null ~elm-constructor)
     (testing-unary-dynamic-null ~elm-constructor)))


(defmacro testing-binary-static-null [elm-constructor non-null-op-1 non-null-op-2]
  `(testing "Static Null"
     (is (nil? (compile {} (~elm-constructor [{:type "Null"} {:type "Null"}]))))
     (is (nil? (compile {} (~elm-constructor [~non-null-op-1 {:type "Null"}]))))
     (is (nil? (compile {} (~elm-constructor [{:type "Null"} ~non-null-op-2]))))))


(defmacro testing-binary-dynamic-null [elm-constructor non-null-op-1 non-null-op-2]
  `(let [context# {:eval-context "Patient" :node (mem-node-with [])}]
     (testing "Dynamic Null"
       (let [elm# (~elm-constructor
                    [#elm/singleton-from patient-retrieve-elm
                     #elm/singleton-from patient-retrieve-elm])
             expr# (compile context# elm#)]
         (is (nil? (-eval expr# {} nil nil))))
       (let [elm# (~elm-constructor
                    [~non-null-op-1
                     #elm/singleton-from patient-retrieve-elm])
             expr# (compile context# elm#)]
         (is (nil? (-eval expr# {} nil nil))))
       (let [elm# (~elm-constructor
                    [#elm/singleton-from patient-retrieve-elm
                     ~non-null-op-2])
             expr# (compile context# elm#)]
         (is (nil? (-eval expr# {} nil nil)))))))


(defmacro testing-binary-null
  ([elm-constructor non-null-op]
   `(testing-binary-null ~elm-constructor ~non-null-op ~non-null-op))
  ([elm-constructor non-null-op-1 non-null-op-2]
   `(do
      (testing-binary-static-null ~elm-constructor ~non-null-op-1 ~non-null-op-2)
      (testing-binary-dynamic-null ~elm-constructor ~non-null-op-1 ~non-null-op-2))))


(defmacro compile-binop [constructor op-constructor op-1 op-2]
  `(compile {} (~constructor [(~op-constructor ~op-1) (~op-constructor ~op-2)])))


(defmethod test/assert-expr 'thrown-anom? [msg form]
  (let [category (second form)
        body (nthnext form 2)]
    `(try ~@body
          (test/do-report {:type :fail, :message ~msg,
                           :expected '~form, :actual nil})
          (catch Exception e#
            (let [m# (::anom/category (ex-data e#))]
              (if (= ~category m#)
                (test/do-report {:type :pass, :message ~msg,
                                 :expected '~form, :actual e#})
                (test/do-report {:type :fail, :message ~msg,
                                 :expected '~form, :actual e#})))
            e#))))


(defn- code
  ([system code]
   (elm/instance ["{urn:hl7-org:elm-types:r1}Code"
                  {"system" #elm/string system "code" #elm/string code}]))
  ([system version code]
   (elm/instance ["{urn:hl7-org:elm-types:r1}Code"
                  {"system" #elm/string system
                   "version" #elm/string version
                   "code" #elm/string code}])))



;; 1. Simple Values

;; 1.1 Literal
(deftest compile-literal-test
  (testing "Boolean Literal"
    (are [elm res] (= res (compile {} elm))
      #elm/boolean "true" true
      #elm/boolean "false" false))

  (testing "Decimal Literal"
    (are [elm res] (= res (compile {} elm))
      #elm/decimal "-1" -1M
      #elm/decimal "0" 0M
      #elm/decimal "1" 1M

      #elm/decimal "-0.1" -0.1M
      #elm/decimal "0.0" 0M
      #elm/decimal "0.1" 0.1M

      #elm/decimal "0.000000001" 0M
      #elm/decimal "0.000000005" 1E-8M))

  (testing "Integer Literal"
    (are [elm res] (= res (compile {} elm))
      #elm/integer "-1" -1
      #elm/integer "0" 0
      #elm/integer "1" 1)))



;; 2. Structured Values

;; 2.1. Tuple
;;
;; The Tuple expression allows tuples of any type to be built up as an
;; expression. The tupleType attribute specifies the type of the tuple being
;; built, if any, and the list of tuple elements specify the values for the
;; elements of the tuple. Note that the value of an element may be any
;; expression, including another Tuple.
(deftest compile-tuple-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
    #elm/tuple {"id" #elm/integer "1"}
    {:id 1}

    #elm/tuple {"id" #elm/integer "1" "name" #elm/string "john"}
    {:id 1 :name "john"}))


;; 2.2. Instance
;;
;; The Instance expression allows class instances of any type to be built up as
;; an expression. The classType attribute specifies the type of the class
;; instance being built, and the list of instance elements specify the values
;; for the elements of the class instance. Note that the value of an element may
;; be any expression, including another Instance.
(deftest compile-instance-test
  (testing "Code"
    (given (compile {} (code "system-134534" "code-134551"))
      type := Code
      :system := "system-134534"
      :code := "code-134551")))


;; 2.3. Property
;;
;; The Property operator returns the value of the property on source specified
;; by the path attribute.
;;
;; If the result of evaluating source is null, the result is null.
;;
;; The path attribute may include qualifiers (.) and indexers ([x]). Indexers
;; must be literal integer values.
;;
;; If the path attribute contains qualifiers or indexers, each qualifier or
;; indexer is traversed to obtain the actual value. If the object of the
;; property access at any point in traversing the path is null, the result is
;; null.
;;
;; If a scope is specified, the name is used to resolve the scope in which the
;; path will be resolved. Scopes can be named by operators such as Filter and
;; ForEach.
;;
;; Property expressions can also be used to access the individual points and
;; closed indicators for interval types using the property names low, high,
;; lowClosed, and highClosed.
(deftest compile-property-test
  (testing "with scope"
    (testing "with entity supplied over query context"
      (testing "Patient.identifier"
        (testing "with source-type"
          (let [elm
                {:path "identifier"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Patient"}
                identifier {:system "foo" :value "bar"}
                entity
                (vary-meta
                  {:resourceType "Patient" :id "0" :identifier [identifier]}
                  assoc :type :fhir/Patient)
                expr
                (compile
                  {:eval-context "Patient"}
                  elm)]
            (testing "property spec is pre-calculated"
              (is (= :fhir/Identifier (:spec expr))))
            (let [result (coll/first (-eval expr nil nil {"R" entity}))]
              (is (= identifier result))
              (is (= :fhir/Identifier (type result))))))

        (testing "without source-type"
          (let [elm
                {:path "identifier"
                 :scope "R"
                 :type "Property"}
                identifier {:system "foo" :value "bar"}
                entity
                (vary-meta
                  {:resourceType "Patient" :id "0" :identifier [identifier]}
                  assoc :type :fhir/Patient)
                expr
                (compile
                  {:eval-context "Patient"}
                  elm)
                result (coll/first (-eval expr nil nil {"R" entity}))]
              (is (= identifier result))
              (is (= :fhir/Identifier (type result))))))

      (testing "Patient.gender"
        (testing "with source-type"
          (let [elm
                {:path "gender"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Patient"}
                entity
                (vary-meta
                  {:resourceType "Patient" :id "0" :gender "male"}
                  assoc :type :fhir/Patient)
                expr
                (compile
                  {:eval-context "Patient"}
                  elm)]
            (testing "property spec is pre-calculated"
              (is (= :fhir/code (:spec expr))))
            (is (= "male" (-eval expr nil nil {"R" entity})))))

        (testing "without source-type"
          (let [elm
                {:path "gender"
                 :scope "R"
                 :type "Property"}
                entity
                (vary-meta
                  {:resourceType "Patient" :id "0" :gender "male"}
                  assoc :type :fhir/Patient)
                expr
                (compile
                  {:eval-context "Patient"}
                  elm)]
            (is (= "male" (-eval expr nil nil {"R" entity}))))))

      (testing "Observation.value"
        (testing "with source-type"
          (let [elm
                {:path "value"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Observation"}
                entity
                (vary-meta
                  {:resourceType "Observation" :id "0" :valueString "value-114318"}
                  assoc :type :fhir/Observation)
                expr
                (compile
                  {:eval-context "Patient"}
                  elm)]
            (testing "choices are pre-calculated"
              (is (coll? (:choices expr))))
            (is (= "value-114318" (-eval expr nil nil {"R" entity})))))

        (testing "without source-type"
          (let [elm
                {:path "value"
                 :scope "R"
                 :type "Property"}
                entity
                (vary-meta
                  {:resourceType "Observation" :id "0" :valueString "value-114318"}
                  assoc :type :fhir/Observation)
                expr
                (compile
                  {:eval-context "Patient"}
                  elm)]
            (is (= "value-114318" (-eval expr nil nil {"R" entity})))))))

    (testing "with entity supplied directly"
      (testing "Patient.identifier"
        (testing "with source-type"
          (let [elm
                {:path "identifier"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Patient"}
                identifier {:system "foo" :value "bar"}
                entity
                (vary-meta
                  {:resourceType "Patient" :id "0" :identifier [identifier]}
                  assoc :type :fhir/Patient)
                expr
                (compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)]
            (testing "property spec is pre-calculated"
              (is (= :fhir/Identifier (:spec expr))))
            (let [result (coll/first (-eval expr nil nil entity))]
              (is (= identifier result))
              (is (= :fhir/Identifier (type result))))))

        (testing "without source-type"
          (let [elm
                {:path "identifier"
                 :scope "R"
                 :type "Property"}
                identifier {:system "foo" :value "bar"}
                entity
                (vary-meta
                  {:resourceType "Patient" :id "0" :identifier [identifier]}
                  assoc :type :fhir/Patient)
                expr
                (compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)
                result (coll/first (-eval expr nil nil entity))]
              (is (= identifier result))
              (is (= :fhir/Identifier (type result))))))

      (testing "Patient.gender"
        (testing "with source-type"
          (let [elm
                {:path "gender"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Patient"}
                entity
                (vary-meta
                  {:resourceType "Patient" :id "0" :gender "male"}
                  assoc :type :fhir/Patient)
                expr
                (compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)]
            (testing "property spec is pre-calculated"
              (is (= :fhir/code (:spec expr))))
            (is (= "male" (-eval expr nil nil entity)))))

        (testing "without source-type"
          (let [elm
                {:path "gender"
                 :scope "R"
                 :type "Property"}
                entity
                (vary-meta
                  {:resourceType "Patient" :id "0" :gender "male"}
                  assoc :type :fhir/Patient)
                expr
                (compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)]
            (is (= "male" (-eval expr nil nil entity))))))

      (testing "Observation.value"
        (testing "with source-type"
          (let [elm
                {:path "value"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Observation"}
                entity
                (vary-meta
                  {:resourceType "Observation" :id "0" :valueString "value-114318"}
                  assoc :type :fhir/Observation)
                expr
                (compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)]
            (testing "choices are pre-calculated"
              (is (coll? (:choices expr))))
            (is (= "value-114318" (-eval expr nil nil entity)))))

        (testing "without source-type"
          (let [elm
                {:path "value"
                 :scope "R"
                 :type "Property"}
                entity
                (vary-meta
                  {:resourceType "Observation" :id "0" :valueString "value-114318"}
                  assoc :type :fhir/Observation)
                expr
                (compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)]
            (is (= "value-114318" (-eval expr nil nil entity))))))))

  (testing "with source"
    (testing "Patient.identifier"
      (testing "with source-type"
        (let [library {:statements {:def [{:name "Patient"}]}}
              elm
              {:path "identifier"
               :source #elm/expression-ref "Patient"
               :type "Property"
               :life/source-type "{http://hl7.org/fhir}Patient"}
              identifier {:system "foo" :value "bar"}
              source
              (vary-meta
                {:resourceType "Patient" :id "0" :identifier [identifier]}
                assoc :type :fhir/Patient)
              expr (compile {:library library :eval-context "Patient"} elm)]
          (testing "property spec is pre-calculated"
            (is (= :fhir/Identifier (:spec expr))))
          (let [result (coll/first (-eval expr {:library-context {"Patient" source}} nil nil))]
            (is (= identifier result))
            (is (= :fhir/Identifier (type result))))))

      (testing "without source-type"
        (let [library {:statements {:def [{:name "Patient"}]}}
              elm
              {:path "identifier"
               :source #elm/expression-ref "Patient"
               :type "Property"}
              identifier {:system "foo" :value "bar"}
              source
              (vary-meta
                {:resourceType "Patient" :id "0" :identifier [identifier]}
                assoc :type :fhir/Patient)
              expr (compile {:library library :eval-context "Patient"} elm)
              result (coll/first (-eval expr {:library-context {"Patient" source}} nil nil))]
            (is (= identifier result))
            (is (= :fhir/Identifier (type result))))))

    (testing "Patient.gender"
      (testing "with source-type"
        (let [library {:statements {:def [{:name "Patient"}]}}
              elm
              {:path "gender"
               :source #elm/expression-ref "Patient"
               :type "Property"
               :life/source-type "{http://hl7.org/fhir}Patient"}
              source
              (vary-meta
                {:resourceType "Patient" :id "0" :gender "male"}
                assoc :type :fhir/Patient)
              expr (compile {:library library :eval-context "Patient"} elm)]
          (testing "property spec is pre-calculated"
            (is (= :fhir/code (:spec expr))))
          (is (= "male" (-eval expr {:library-context {"Patient" source}} nil nil)))))

      (testing "without source-type"
        (let [library {:statements {:def [{:name "Patient"}]}}
              elm
              {:path "gender"
               :source #elm/expression-ref "Patient"
               :type "Property"}
              source
              (vary-meta
                {:resourceType "Patient" :id "0" :gender "male"}
                assoc :type :fhir/Patient)
              expr (compile {:library library :eval-context "Patient"} elm)]
          (is (= "male" (-eval expr {:library-context {"Patient" source}} nil nil))))))

    (testing "Observation.value"
      (testing "with source-type"
        (let [library {:statements {:def [{:name "Observation"}]}}
              elm
              {:path "value"
               :source #elm/expression-ref "Observation"
               :type "Property"
               :life/source-type "{http://hl7.org/fhir}Observation"}
              source
              (vary-meta
                {:resourceType "Observation" :id "0" :valueString "value-114318"}
                assoc :type :fhir/Observation)
              expr (compile {:library library :eval-context "Patient"} elm)]
          (testing "choices are pre-calculated"
            (is (coll? (:choices expr))))
          (is (= "value-114318" (-eval expr {:library-context {"Observation" source}} nil nil)))))

      (testing "without source-type"
        (let [library {:statements {:def [{:name "Observation"}]}}
              elm
              {:path "value"
               :source #elm/expression-ref "Observation"
               :type "Property"}
              source
              (vary-meta
                {:resourceType "Observation" :id "0" :valueString "value-114318"}
                assoc :type :fhir/Observation)
              expr (compile {:library library :eval-context "Patient"} elm)]
          (is (= "value-114318" (-eval expr {:library-context {"Observation" source}} nil nil))))))

    (testing "Tuple"
      (are [elm result]
        (= result (-eval (compile {:eval-context "Unspecified"} elm) {} nil nil))
        {:resultTypeName "{urn:hl7-org:elm-types:r1}Integer"
         :path "id"
         :type "Property"
         :source
         {:type "Tuple"
          :resultTypeSpecifier
          {:type "TupleTypeSpecifier"
           :element
           [{:name "id"
             :type {:name "{urn:hl7-org:elm-types:r1}Integer" :type "NamedTypeSpecifier"}}
            {:name "name"
             :type {:name "{urn:hl7-org:elm-types:r1}String" :type "NamedTypeSpecifier"}}]}
          :element
          [{:name "id" :value #elm/integer "1"}]}}
        1))

    (testing "Quantity"
      (testing "value"
        (are [elm result]
          (= result (-eval (compile {:eval-context "Unspecified"} elm) {} nil nil))
          {:resultTypeName "{urn:hl7-org:elm-types:r1}Decimal"
           :path "value"
           :type "Property"
           :source #elm/quantity [42 "m"]}
          42))

      (testing "unit"
        (are [elm result]
          (= result (-eval (compile {:eval-context "Unspecified"} elm) {} nil nil))
          {:resultTypeName "{urn:hl7-org:elm-types:r1}String"
           :path "unit"
           :type "Property"
           :source #elm/quantity [42 "m"]}
          "m")))

    (testing "nil"
      (are [elm result]
        (= result (-eval (compile {:eval-context "Unspecified"} elm) {} nil nil))
        {:path "value"
         :type "Property"
         :source {:type "Null"}}
        nil))))



;; 3. Clinical Values

;; 3.1. Code
;;
;; The Code type represents a literal code selector.
(deftest compile-code-test
  (testing "without version"
    (let [context
          {:library
           {:codeSystems
            {:def [{:name "sys-def-115852" :id "system-115910"}]}}}]
      (given (compile context #elm/code ["sys-def-115852" "code-115927"])
        type := Code
        :system := "system-115910"
        :code := "code-115927")))

  (testing "with-version"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-120434"
               :id "system-120411"
               :version "version-120408"}]}}}]
      (given (compile context #elm/code ["sys-def-120434" "code-120416"])
        type := Code
        :system := "system-120411"
        :version := "version-120408"
        :code := "code-120416"))))


;; 3.2. CodeDef
;;
;; Only use indirectly through CodeRef.


;; 3.3. CodeRef
;;
;; The CodeRef expression allows a previously defined code to be referenced
;; within an expression.
(deftest compile-code-ref-test
  (testing "without version"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-125149"
               :id "system-name-125213"}]}
            :codes
            {:def
             [{:name "code-def-125054"
               :id "code-125340"
               :codeSystem {:name "sys-def-125149"}}]}}}]
      (given (compile context #elm/code-ref "code-def-125054")
        type := Code
        :system := "system-name-125213"
        :code := "code-125340")))

  (testing "with version"
    (let [context
          {:library
           {:codeSystems
            {:def
             [{:name "sys-def-125149"
               :id "system-name-125213"
               :version "version-125222"}]}
            :codes
            {:def
             [{:name "code-def-125054"
               :id "code-125354"
               :codeSystem {:name "sys-def-125149"}}]}}}]
      (given (compile context #elm/code-ref "code-def-125054")
        type := Code
        :system := "system-name-125213"
        :version := "version-125222"
        :code := "code-125354"))))


;; 3.4. CodeSystemDef
;;
;; Only used indirectly through Code and CodeDef.


;; 3.5. CodeSystemRef
;;
;; Only used indirectly through Code and CodeDef.


;; 3.6. Concept
;;
;; The Concept type represents a literal concept selector.
;; TODO


;; 3.9. Quantity
;;
;; The Quantity type defines a clinical quantity. For example, the quantity 10
;; days or 30 mmHg. The value is a decimal, while the unit is expected to be a
;; valid UCUM unit.
(deftest compile-quantity-test
  (testing "Examples"
    (are [elm res] (= res (compile {} elm))
      {:type "Quantity"} nil
      #elm/quantity [1] (quantity 1 "")
      #elm/quantity [1 "year"] (period 1 0 0)
      #elm/quantity [2 "years"] (period 2 0 0)
      #elm/quantity [1 "month"] (period 0 1 0)
      #elm/quantity [2 "months"] (period 0 2 0)
      #elm/quantity [1 "week"] (period 0 0 (* 7 24 60 60 1000))
      #elm/quantity [2 "weeks"] (period 0 0 (* 2 7 24 60 60 1000))
      #elm/quantity [1 "day"] (period 0 0 (* 24 60 60 1000))
      #elm/quantity [2 "days"] (period 0 0 (* 2 24 60 60 1000))
      #elm/quantity [1 "hour"] (period 0 0 (* 60 60 1000))
      #elm/quantity [2 "hours"] (period 0 0 (* 2 60 60 1000))
      #elm/quantity [1 "minute"] (period 0 0 (* 60 1000))
      #elm/quantity [2 "minutes"] (period 0 0 (* 2 60 1000))
      #elm/quantity [1 "second"] (period 0 0 1000)
      #elm/quantity [2 "seconds"] (period 0 0 2000)
      #elm/quantity [1 "millisecond"] (period 0 0 1)
      #elm/quantity [2 "milliseconds"] (period 0 0 2)
      #elm/quantity [1 "s"] (quantity 1 "s")
      #elm/quantity [1 "cm2"] (quantity 1 "cm2")))

  (testing "Periods"
    (satisfies-prop 100
                    (prop/for-all [period (s/gen :elm/period)]
                      (#{BigDecimal Period} (type (-eval (compile {} period) {} nil nil)))))))



;; 9. Reusing Logic

;; 9.2. ExpressionRef
;;
;; The ExpressionRef type defines an expression that references a previously
;; defined NamedExpression. The result of evaluating an ExpressionReference is
;; the result of evaluating the referenced NamedExpression.
(deftest compile-expression-ref-test
  (testing "Throws error on missing expression"
    (is (thrown-anom? ::anom/incorrect (compile {} #elm/expression-ref "name-170312"))))

  (testing "Result Type"
    (let [library {:statements {:def [{:name "name-170312" :resultTypeName "result-type-name-173029"}]}}
          expr (compile {:library library} #elm/expression-ref "name-170312")]
      (is (= "result-type-name-173029" (:result-type-name (meta expr))))))

  (testing "Eval"
    (let [library {:statements {:def [{:name "name-170312"}]}}
          expr (compile {:library library} #elm/expression-ref "name-170312")]
      (is (= ::result (-eval expr {:library-context {"name-170312" ::result}} nil nil))))))


;; 9.4. FunctionRef
(deftest compile-function-ref-test
  (testing "ToString"
    (are [elm res]
      (= res (-eval (compile {} elm) {} nil nil))
      {:type "FunctionRef"
       :libraryName "FHIRHelpers"
       :name "ToString"
       :operand [#elm/string "foo"]}
      "foo"))

  (testing "ToQuantity"
    (let [context {:eval-context "Patient" :node (mem-node-with [])}
          elm {:type "FunctionRef"
               :libraryName "FHIRHelpers"
               :name "ToQuantity"
               :operand [#elm/singleton-from patient-retrieve-elm]}]
      (are [resource res]
        (= res (-eval (compile context elm) {} resource nil))
        {:value 23M :code "kg"} (quantity 23M "kg")
        {:value 42M} (quantity 42M "1")
        {} nil))))



;; 10. Queries

;; 10.1. Query
;;
;; The Query operator represents a clause-based query. The result of the query
;; is determined by the type of sources included, as well as the clauses used
;; in the query.
(deftest compile-query-test
  (testing "Non-retrieve queries"
    (testing "Sort"
      (testing "ByDirection"
        (are [query res] (= res (-eval (compile {} query) {} nil nil))
          {:type "Query"
           :source
           [{:alias "S"
             :expression #elm/list [#elm/integer "2" #elm/integer "1" #elm/integer "1"]}]
           :sort {:by [{:type "ByDirection" :direction "asc"}]}}
          [1 2]))

      (testing "ByExpression"
        (are [query res] (= res (-eval (compile {} query) {} nil nil))
          {:type "Query"
           :source
           [{:alias "S"
             :expression
             #elm/list
                 [#elm/quantity [2 "m"]
                  #elm/quantity [1 "m"]
                  #elm/quantity [1 "m"]]}]
           :sort
           {:by
            [{:type "ByExpression"
              :direction "asc"
              :expression
              {:type "Property"
               :path "value"
               :scope "S"
               :resultTypeName "{urn:hl7-org:elm-types:r1}decimal"}}]}}
          [(quantity 1 "m") (quantity 2 "m")])))

    (testing "Return non-distinct"
      (are [query res] (= res (-eval (compile {} query) {} nil nil))
        {:type "Query"
         :source
         [{:alias "S"
           :expression #elm/list [#elm/integer "1" #elm/integer "1"]}]
         :return {:distinct false :expression {:type "AliasRef" :name "S"}}}
        [1 1]))

    (testing "returns only the first item on optimize first"
      (let [query {:type "Query"
                   :source
                   [{:alias "S"
                     :expression #elm/list [#elm/integer "1" #elm/integer "1"]}]}
            res (-eval (compile {:optimizations #{:first}} query) {} nil nil)]
        (is (not (instance? IPersistentCollection res))))))

  (testing "Retrieve queries"
    (let [node (mem-node-with
                 [[[:put {:resourceType "Patient" :id "0"}]]])
          db (d/db node)
          retrieve {:type "Retrieve" :dataType "{http://hl7.org/fhir}Patient"}
          where {:type "Equal"
                 :operand
                 [{:path "gender"
                   :scope "P"
                   :type "Property"
                   :resultTypeName "{http://hl7.org/fhir}string"
                   :life/source-type "{http://hl7.org/fhir}Patient"}
                  #elm/integer "2"]}
          return {:path "gender"
                  :scope "P"
                  :type "Property"
                  :resultTypeName "{http://hl7.org/fhir}string"
                  :life/source-type "{http://hl7.org/fhir}Patient"}]

      (let [query {:type "Query"
                   :source
                   [{:alias "P"
                     :expression retrieve}]}]
        (given (-eval (compile {:node node :eval-context "Unspecified"} query) {:db db} nil nil)
          [0 :resourceType] := "Patient"
          [0 :id] := "0"))

      (let [query {:type "Query"
                   :source
                   [{:alias "P"
                     :expression retrieve}]
                   :where where}]
        (is (empty? (-eval (compile {:node node :eval-context "Unspecified"} query) {:db db} nil nil))))

      (let [query {:type "Query"
                   :source
                   [{:alias "P"
                     :expression retrieve}]
                   :return {:expression return}}]
        (is (nil? (first (-eval (compile {:node node :eval-context "Unspecified"} query) {:db db} nil nil)))))

      (let [query {:type "Query"
                   :source
                   [{:alias "P"
                     :expression retrieve}]
                   :where where
                   :return {:expression return}}]
        (is (empty? (-eval (compile {:node node :eval-context "Unspecified"} query) {:db db} nil nil)))))))


;; 10.3. AliasRef
(deftest compile-alias-ref-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil {"foo" ::result}))
    {:type "AliasRef" :name "foo"}
    ::result))


;; 10.12. With
(deftest compile-with-clause-test
  (retrieve-test/stub-expr
    ::node "Unspecified" "Observation" "code" nil?
    (reify Expression
      (-eval [_ _ _ _]
        [{:resourceType "Observation" :subject {:reference "Patient/0"}}])))

  (testing "Equiv With with two Observations comparing there subjects."
    (let [elm {:alias "O1"
               :type "WithEquiv"
               :expression
               {:type "Retrieve"
                :dataType "{http://hl7.org/fhir}Observation"}
               :equivOperand
               [{:path "subject"
                 :scope "O0"
                 :type "Property"
                 :resultTypeName "{http://hl7.org/fhir}Reference"
                 :life/scopes #{"O0"}
                 :life/source-type "{http://hl7.org/fhir}Observation"}
                {:path "subject"
                 :scope "O1"
                 :type "Property"
                 :resultTypeName "{http://hl7.org/fhir}Reference"
                 :life/scopes #{"O1"}
                 :life/source-type "{http://hl7.org/fhir}Observation"}]}
          compile-context
          {:node ::node :life/single-query-scope "O0" :eval-context "Unspecified"}
          xform-factory (compile-with-equiv-clause compile-context elm)
          eval-context {:db ::db}
          xform (query/-create xform-factory eval-context nil)
          lhs-entity {:resourceType "Observation" :subject {:reference "Patient/0"}}]
      (is (= [lhs-entity] (into [] xform [lhs-entity])))))

  (testing "Equiv With with one Patient and one Observation comparing the patient with the operation subject."
    (let [elm {:alias "O"
               :type "WithEquiv"
               :expression
               {:type "Retrieve"
                :dataType "{http://hl7.org/fhir}Observation"}
               :equivOperand
               [{:name "P" :type "AliasRef" :life/scopes #{"P"}}
                {:path "subject"
                 :scope "O"
                 :type "Property"
                 :resultTypeName "{http://hl7.org/fhir}Reference"
                 :life/scopes #{"O"}
                 :life/source-type "{http://hl7.org/fhir}Observation"}]}
          compile-context
          {:node ::node :life/single-query-scope "P" :eval-context "Unspecified"}
          xform-factory (compile-with-equiv-clause compile-context elm)
          eval-context {:db ::db}
          xform (query/-create xform-factory eval-context nil)
          lhs-entity {:reference "Patient/0"}]
      (is (= [lhs-entity] (into [] xform [lhs-entity]))))))



;; 11. External Data

;; 11.1. Retrieve
;;
;; All access to external data within ELM is represented by Retrieve expressions.
;;
;; The Retrieve class defines the data type of the request, which determines the
;; type of elements to be returned. The result will always be a list of values
;; of the type specified in the request.
;;
;; The type of the elements to be returned is specified with the dataType
;; attribute of the Retrieve, and must refer to the name of a type within a
;; known data model specified in the dataModels element of the library
;; definition.
;;
;; In addition, the Retrieve introduces the ability to specify optional criteria
;; for the request. The available criteria are intentionally restricted to the
;; set of codes involved, and the date range involved. If these criteria are
;; omitted, the request is interpreted to mean all data of that type.
;;
;; Note that because every expression is being evaluated within a context (such
;; as Patient, Practitioner, or Unfiltered) as defined by the containing
;; ExpressionDef, the data returned by a retrieve depends on the context. For
;; example, for the Patient context, the data is returned for a single patient
;; only, as defined by the evaluation environment. Whereas for the Unfiltered
;; context, the data is returned for the entire source.
(deftest compile-retrieve-test
  (testing "without related context"
    (testing "Patient"
      (let [patient {:resourceType "Patient" :id "0"}
            node (mem-node-with [[[:put patient]]])
            context
            {:node node
             :eval-context "Patient"
             :library {}}
            expr (compile context patient-retrieve-elm)]
        (given (-eval expr {:db (d/db node)} patient nil)
          [0 :resourceType] := "Patient"
          [0 :id] := "0")))

    (testing "without codes"
      (let [patient {:resourceType "Patient" :id "0"}
            node (mem-node-with
                   [[[:put patient]
                     [:put {:resourceType "Observation" :id "1"
                            :subject {:reference "Patient/0"}}]]])
            context
            {:node node
             :eval-context "Patient"
             :library {}}
            elm {:type "Retrieve" :dataType "{http://hl7.org/fhir}Observation"}
            expr (compile context elm)]
        (given (-eval expr {:db (d/db node)} patient nil)
          [0 :resourceType] := "Observation"
          [0 :id] := "1")))

    (testing "with codes"
      (let [patient {:resourceType "Patient" :id "0"}
            node (mem-node-with
                   [[[:put patient]
                     [:put {:resourceType "Observation" :id "0"
                            :subject {:reference "Patient/0"}}]
                     [:put {:resourceType "Observation" :id "1"
                            :code
                            {:coding
                             [{:system "system-192253"
                               :code "code-192300"}]}
                            :subject {:reference "Patient/0"}}]]])
            context
            {:node node
             :eval-context "Patient"
             :library
             {:codeSystems
              {:def
               [{:name "sys-def-192450"
                 :id "system-192253"}]}
              :codes
              {:def
               [{:name "code-def-133853"
                 :id "code-192300"
                 :codeSystem {:name "sys-def-192450"}}]}}}
            elm {:type "Retrieve"
                 :dataType "{http://hl7.org/fhir}Observation"
                 :codes #elm/to-list #elm/code-ref "code-def-133853"}
            expr (compile context elm)]
        (given (-eval expr {:db (d/db node)} patient nil)
          [0 :resourceType] := "Observation"
          [0 :id] := "1"))))

  (testing "with related context"
    (testing "with pre-compiled database query"
      (let [node (mem-node-with [])
            library {:codeSystems
                     {:def [{:name "sys-def-174848" :id "system-174915"}]}
                     :statements
                     {:def
                      [{:name "name-174207"
                        :resultTypeName "{http://hl7.org/fhir}Patient"}]}}
            elm {:type "Retrieve"
                 :dataType "{http://hl7.org/fhir}Observation"
                 :context #elm/expression-ref "name-174207"
                 :codes #elm/list [#elm/code ["sys-def-174848" "code-174911"]]}
            expr (compile {:library library :node node} elm)]
        (given expr
          type := WithRelatedContextQueryRetrieveExpression)))))



;; 12. Comparison Operators

;; 12.1. Equal
;;
;; The Equal operator returns true if the arguments are equal; false if the
;; arguments are known unequal, and null otherwise. Equality semantics are
;; defined to be value-based.
;;
;; For simple types, this means that equality returns true if and only if the
;; result of each argument evaluates to the same value.
;;
;; For string values, equality is strictly lexical based on the Unicode values
;; for the individual characters in the strings.
;;
;; For decimal values, trailing zeroes are ignored.
;;
;; For quantities, this means that the dimensions of each quantity must be the
;; same, but not necessarily the unit. For example, units of 'cm' and 'm' are
;; comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For ratios, this means that the numerator and denominator must be the same,
;; using quantity equality semantics.
;;
;; For tuple types, this means that equality returns true if and only if the
;; tuples are of the same type, and the values for all elements that have
;; values, by name, are equal.
;;
;; For list types, this means that equality returns true if and only if the
;; lists contain elements of the same type, have the same number of elements,
;; and for each element in the lists, in order, the elements are equal using
;; equality semantics, with the exception that null elements are considered
;; equal.
;;
;; For interval types, equality returns true if and only if the intervals are
;; over the same point type, and they have the same value for the starting and
;; ending points of the interval as determined by the Start and End operators.
;;
;; For Date, DateTime, and Time values, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the values are different, the comparison stops and the result
;; is false. If one input has a value for the precision and the other does not,
;; the comparison stops and the result is null; if neither input has a value for
;; the precision or the last precision has been reached, the comparison stops
;; and the result is true. For the purposes of comparison, seconds and
;; milliseconds are combined as a single precision using a decimal, with decimal
;; equality semantics.
;;
;; If either argument is null, the result is null.
(deftest compile-equal-test
  (testing "Integer"
    (are [x y res] (= res (compile-binop elm/equal elm/integer x y))
       "1"  "1" true
       "1"  "2" false
       "2"  "1" false)

    (testing-binary-null elm/equal #elm/integer "1"))

  (testing "Decimal"
    (are [x y res] (= res (compile-binop elm/equal elm/decimal x y))
       "1.1"  "1.1" true
       "1.1"  "2.1" false
       "2.1"  "1.1" false

       "1.1"  "1.10" true
       "1.10"  "1.1" true)

    (testing-binary-null elm/equal #elm/decimal "1.1"))

  (testing "Mixed Integer Decimal"
    (are [x y res] (= res (compile {} #elm/equal [x y]))
      #elm/integer "1" #elm/decimal "1" true
      #elm/decimal "1" #elm/integer "1" true))

  (testing "Mixed Integer String"
    (are [x y res] (= res (compile {} #elm/equal [x y]))
      #elm/integer "1" #elm/string "1" false
      #elm/string "1" #elm/integer "1" false))

  (testing "Mixed Decimal String"
    (are [x y res] (= res (compile {} #elm/equal [x y]))
      #elm/decimal "1" #elm/string "1" false
      #elm/string "1" #elm/decimal "1" false))

  (testing "String"
    (are [x y res] (= res (compile-binop elm/equal elm/string x y))
       "a"  "a" true
       "a"  "b" false
       "b"  "a" false)

    (testing-binary-null elm/equal #elm/string "a"))

  (testing "Quantity"
    (are [x y res] (= res (compile-binop elm/equal elm/quantity x y))
       [1]  [1] true
       [1]  [2] false

       [1 "s"]  [1 "s"] true
       [1 "m"]  [1 "m"] true
       [100 "cm"]  [1 "m"] true
       [1 "s"]  [2 "s"] false
       [1 "s"]  [1 "m"] false)

    (testing-binary-null elm/equal #elm/quantity [1]))

  ;; TODO: Ratio

  ;; TODO: Tuple

  (testing "List"
    (are [x y res] (= res (compile-binop elm/equal elm/list x y))
       [#elm/integer "1"]  [#elm/integer "1"] true
       []  [] true

       [#elm/integer "1"]  [] false
       [#elm/integer "1"]  [#elm/integer "2"] false
       [#elm/integer "1" #elm/integer "1"]
       [#elm/integer "1" #elm/integer "2"] false

       [#elm/integer "1" {:type "Null"}] [#elm/integer "1" {:type "Null"}] nil
       [{:type "Null"}]  [{:type "Null"}] nil
       [#elm/date "2019"]  [#elm/date "2019-01"] nil)

    (testing-binary-null elm/equal #elm/list []))

  (testing "Interval"
    (are [x y res] (= res (compile-binop elm/equal elm/interval x y))
      [#elm/integer "1" #elm/integer "2"]
      [#elm/integer "1" #elm/integer "2"] true)

    (testing-binary-null elm/equal #elm/interval [#elm/integer "1" #elm/integer "2"]))

  (testing "Date with year precision"
    (are [x y res] (= res (compile-binop elm/equal elm/date x y))
       "2013"  "2013" true
       "2012"  "2013" false
       "2013"  "2012" false)

    (testing-binary-null elm/equal #elm/date "2013"))

  (testing "Date with year-month precision"
    (are [x y res] (= res (compile-binop elm/equal elm/date x y))
       "2013-01"  "2013-01" true
       "2013-01"  "2013-02" false
       "2013-02"  "2013-01" false)

    (testing-binary-null elm/equal #elm/date "2013-01"))

  (testing "Date with full precision"
    (are [x y res] (= res (compile-binop elm/equal elm/date x y))
       "2013-01-01"  "2013-01-01" true
       "2013-01-01"  "2013-01-02" false
       "2013-01-02"  "2013-01-01" false)

    (testing-binary-null elm/equal #elm/date "2013-01-01"))

  (testing "Date with differing precisions"
    (are [x y res] (= res (compile-binop elm/equal elm/date x y))
       "2013"  "2013-01" nil))

  (testing "Today() = Today()"
    (are [a b] (true? (-eval (compile {} #elm/equal [a b]) {:now now} nil nil))
      {:type "Today"} {:type "Today"}))

  (testing "DateTime with full precision (there is only one precision)"
    (are [x y res] (= res (compile-binop elm/equal elm/date-time x y))
       "2013-01-01T00:00:00" "2013-01-01T00:00:00" true

       "2013-01-01T00:00" "2013-01-01T00:00:00" true

       "2013-01-01T00" "2013-01-01T00:00:00" true)

    (testing-binary-null elm/equal #elm/date-time "2013-01-01"))

  (testing "Time"
    (are [x y res] (= res (compile-binop elm/equal elm/time x y))
       "12:30:15"  "12:30:15" true
       "12:30:15"  "12:30:16" false
       "12:30:16"  "12:30:15" false

       "12:30.00"  "12:30" nil

       "12:00"  "12" nil)

    (testing-binary-null elm/equal #elm/time "12:30:15"))

  (testing "Code"
    (are [a b res] (= res (-eval (compile {} #elm/equal [a b]) {} nil nil))
      (code "a" "0") (code "a" "0") true
      (code "a" "0") (code "a" "1") false
      (code "a" "0") (code "b" "0") false

      (code "a" "0") (code "a" "2010" "0") false
      (code "a" "2010" "0") (code "a" "0") false

      (code "a" "2010" "0") (code "a" "2020" "0") false
      (code "a" "2020" "0") (code "a" "2010" "0") false)

    (testing-binary-null elm/equal (code "a" "0"))))


;; 12.2. Equivalent
;;
;; The Equivalent operator returns true if the arguments are the same value, or
;; if they are both null; and false otherwise.
;;
;; With the exception of null behavior and the semantics for specific types
;; defined below, equivalence is the same as equality.
;;
;; For string values, equivalence returns true if the strings are the same value
;; while ignoring case and locale, and normalizing whitespace. Normalizing
;; whitespace means that all whitespace characters are treated as equivalent,
;; with whitespace characters as defined in the whitespace lexical category.
;;
;; For decimals, equivalent means the values are the same with the comparison
;; done on values rounded to the precision of the least precise operand;
;; trailing zeroes after the decimal are ignored in determining precision for
;; equivalent comparison.
;;
;; For quantities, equivalent means the values are the same quantity when
;; considering unit conversion (e.g. 100 'cm' ~ 1 'm') and using decimal
;; equivalent semantics for the value. Note that implementations are not
;; required to support unit conversion and so are allowed to return null for
;; equivalence of quantities with different units.
;;
;; For ratios, equivalent means that the numerator and denominator represent the
;; same ratio (e.g. 1:100 ~ 10:1000).
;;
;; For tuple types, this means that two tuple values are equivalent if and only
;; if the tuples are of the same type, and the values for all elements by name
;; are equivalent.
;;
;; For list types, this means that two lists are equivalent if and only if the
;; lists contain elements of the same type, have the same number of elements,
;; and for each element in the lists, in order, the elements are equivalent.
;;
;; For interval types, this means that two intervals are equivalent if and only
;; if the intervals are over the same point type, and the starting and ending
;; points of the intervals as determined by the Start and End operators are
;; equivalent.
;;
;; For Date, DateTime, and Time values, the comparison is performed in the same
;; way as it is for equality, except that if one input has a value for a given
;; precision and the other does not, the comparison stops and the result is
;; false, rather than null. As with equality, the second and millisecond
;; precisions are combined and combined as a single precision using a decimal,
;; with decimal equivalence semantics.
;;
;; For Code values, equivalence is defined based on the code and system elements
;; only. The display and version elements are ignored for the purposes of
;; determining Code equivalence.
;;
;; For Concept values, equivalence is defined as a non-empty intersection of the
;; codes in each Concept.
;;
;; Note that this operator will always return true or false, even if either or
;; both of its arguments are null or contain null components.
(deftest compile-equivalent-test
  (testing "Both null"
    (are [a b res] (= res (-eval (compile {} #elm/equivalent [a b]) {} nil nil))
      {:type "Null"} {:type "Null"} true))

  (testing "Boolean"
    (are [a b res] (= res (-eval (compile {} #elm/equivalent [a b]) {} nil nil))
      #elm/boolean "true" #elm/boolean "true" true
      #elm/boolean "true" #elm/boolean "false" false

      {:type "Null"} #elm/boolean "true" false
      #elm/boolean "true" {:type "Null"} false))

  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} #elm/equivalent [a b]) {} nil nil))
      #elm/integer "1" #elm/integer "1" true
      #elm/integer "1" #elm/integer "2" false

      {:type "Null"} #elm/integer "1" false
      #elm/integer "1" {:type "Null"} false))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} #elm/equivalent [a b]) {} nil nil))
      #elm/decimal "1.1" #elm/decimal "1.1" true
      #elm/decimal "1.1" #elm/decimal "2.1" false

      {:type "Null"} #elm/decimal "1.1" false
      #elm/decimal "1.1" {:type "Null"} false))

  (testing "Mixed Integer Decimal"
    (are [a b res] (= res (-eval (compile {} #elm/equivalent [a b]) {} nil nil))
      #elm/integer "1" #elm/decimal "1" true
      #elm/decimal "1" #elm/integer "1" true))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} #elm/equivalent [a b]) {} nil nil))
      #elm/quantity [1] #elm/quantity [1] true
      #elm/quantity [1] #elm/quantity [2] false

      #elm/quantity [1 "s"] #elm/quantity [1 "s"] true
      #elm/quantity [1 "m"] #elm/quantity [1 "m"] true
      #elm/quantity [100 "cm"] #elm/quantity [1 "m"] true
      #elm/quantity [1 "s"] #elm/quantity [2 "s"] false
      #elm/quantity [1 "s"] #elm/quantity [1 "m"] false

      {:type "Null"} #elm/quantity [1] false
      #elm/quantity [1] {:type "Null"} false

      {:type "Null"} #elm/quantity [1 "s"] false
      #elm/quantity [1 "s"] {:type "Null"} false))

  (testing "List"
    (are [x y res] (= res (-eval (compile {} (elm/equivalent [x y])) {} nil nil))
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "1"] true
      #elm/list [] #elm/list [] true

      #elm/list [#elm/integer "1"] #elm/list [] false
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "2"] false
      #elm/list [#elm/integer "1" #elm/integer "1"]
      #elm/list [#elm/integer "1" #elm/integer "2"] false

      #elm/list [#elm/integer "1" {:type "Null"}]
      #elm/list [#elm/integer "1" {:type "Null"}] true
      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] true
      #elm/list [#elm/date "2019"] #elm/list [#elm/date "2019-01"] false

      {:type "Null"} #elm/list [] false
      #elm/list [] {:type "Null"} false))

  (testing "Code"
    (are [a b res] (= res (-eval (compile {} #elm/equivalent [a b]) {} nil nil))
      (code "a" "0") (code "a" "0") true
      (code "a" "0") (code "a" "1") false
      (code "a" "0") (code "b" "0") false

      (code "a" "0") (code "a" "2010" "0") true
      (code "a" "2010" "0") (code "a" "0") true

      (code "a" "2010" "0") (code "a" "2020" "0") true
      (code "a" "2020" "0") (code "a" "2010" "0") true

      {:type "Null"} (code "a" "0") false
      (code "a" "0") {:type "Null"} false)))


;; 12.3. Greater
;;
;; The Greater operator returns true if the first argument is greater than the
;; second argument.
;;
;; For comparisons involving quantities, the dimensions of each quantity must be
;; the same, but not necessarily the unit. For example, units of 'cm' and 'm'
;; are comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For Date, DateTime, and Time values, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the first value is greater than the second, the result is true;
;; if the first value is less than the second, the result is false; if one input
;; has a value for the precision and the other does not, the comparison stops
;; and the result is null; if neither input has a value for the precision or the
;; last precision has been reached, the comparison stops and the result is
;; false. For the purposes of comparison, seconds and milliseconds are combined
;; as a single precision using a decimal, with decimal comparison semantics.
;;
;; If either argument is null, the result is null.
;;
;; The Greater operator is defined for the Integer, Decimal, String, Date,
;; DateTime, Time, and Quantity types.
(deftest compile-greater-test
  (testing "Integer"
    (are [a b res] (= res (compile-binop elm/greater elm/integer a b))
      "2" "1" true
      "1" "1" false)

    (testing-binary-null elm/greater #elm/integer "1"))

  (testing "Decimal"
    (are [a b res] (= res (compile-binop elm/greater elm/decimal a b))
      "2.1" "1.1" true
      "1.1" "1.1" false)

    (testing-binary-null elm/greater #elm/decimal "1.1"))

  (testing "String"
    (are [a b res] (= res (compile-binop elm/greater elm/string a b))
      "b" "a" true
      "a" "a" false)

    (testing-binary-null elm/greater #elm/string "a"))

  (testing "Quantity"
    (are [a b res] (= res (compile-binop elm/greater elm/quantity a b))
      [2] [1] true
      [1] [1] false

      [2 "s"] [1 "s"] true
      [2 "m"] [1 "m"] true
      [101 "cm"] [1 "m"] true
      [1 "s"] [1 "s"] false
      [1 "m"] [1 "m"] false
      [100 "cm"] [1 "m"] false)

    (testing-binary-null elm/greater #elm/quantity [1]))

  (testing "Date with year precision"
    (are [a b res] (= res (compile-binop elm/greater elm/date a b))
      "2014" "2013" true
      "2013" "2013" false)

    (testing-binary-null elm/greater #elm/date "2013"))

  (testing "Comparing dates with mixed precisions (year and year-month) results in null."
    (are [a b res] (= res (compile-binop elm/greater elm/date a b))
      "2013" "2013-01" nil
      "2013-01" "2013" nil))

  (testing "Time"
    (are [a b res] (= res (compile-binop elm/greater elm/time a b))
      "00:00:01" "00:00:00" true
      "00:00:00" "00:00:00" false)

    (testing-binary-null elm/greater #elm/time "00:00:00")))


;; 12.4. GreaterOrEqual
;;
;; The GreaterOrEqual operator returns true if the first argument is greater
;; than or equal to the second argument.
;;
;; For comparisons involving quantities, the dimensions of each quantity must be
;; the same, but not necessarily the unit. For example, units of 'cm' and 'm'
;; are comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For Date, DateTime, and Time values, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the first value is greater than the second, the result is true;
;; if the first value is less than the second, the result is false; if one input
;; has a value for the precision and the other does not, the comparison stops
;; and the result is null; if neither input has a value for the precision or the
;; last precision has been reached, the comparison stops and the result is true.
;; For the purposes of comparison, seconds and milliseconds are combined as a
;; single precision using a decimal, with decimal comparison semantics.
;;
;; If either argument is null, the result is null.
;;
;; The GreaterOrEqual operator is defined for the Integer, Decimal, String,
;; Date, DateTime, Time, and Quantity types.
(deftest compile-greater-or-equal-test
  (testing "Integer"
    (are [a b res] (= res (compile-binop elm/greater-or-equal elm/integer a b))
      "1" "1" true
      "2" "1" true
      "1" "2" false)

    (testing-binary-null elm/greater-or-equal #elm/integer "1"))

  (testing "Decimal"
    (are [a b res] (= res (compile-binop elm/greater-or-equal elm/decimal a b))
      "1.1" "1.1" true
      "2.1" "1.1" true
      "1.1" "2.1" false)

    (testing-binary-null elm/greater-or-equal #elm/decimal "1.1"))

  (testing "String"
    (are [a b res] (= res (compile-binop elm/greater-or-equal elm/string a b))
      "a" "a" true
      "b" "a" true
      "a" "b" false)

    (testing-binary-null elm/greater-or-equal #elm/string "a"))

  (testing "Date"
    (are [a b res] (= res (compile-binop elm/greater-or-equal elm/date a b))
      "2013" "2013" true
      "2014" "2013" true
      "2013" "2014" false

      "2014-01" "2014" nil
      "2014" "2014-01" nil)

    (testing-binary-null elm/greater-or-equal #elm/date "2014"))

  (testing "DateTime"
    (are [a b res] (= res (compile-binop elm/greater-or-equal elm/date-time a b))
       "2013"  "2013" true
       "2014"  "2013" true
       "2013"  "2014" false

       "2014-01"  "2014" nil
       "2014"  "2014-01" nil)

    (testing-binary-null elm/greater-or-equal #elm/date-time "2014"))

  (testing "Time"
    (are [a b res] (= res (compile-binop elm/greater-or-equal elm/time a b))
       "00:00:00"  "00:00:00" true
       "00:00:01"  "00:00:00" true
       "00:00:00"  "00:00:01" false)

    (testing-binary-null elm/greater-or-equal #elm/time "00:00:00"))

  (testing "Quantity"
    (are [a b res] (= res (compile-binop elm/greater-or-equal elm/quantity a b))
       [1]  [1] true
       [2]  [1] true
       [1]  [2] false

       [1 "s"]  [1 "s"] true
       [2 "s"]  [1 "s"] true
       [1 "s"]  [2 "s"] false

       [101 "cm"]  [1 "m"] true
       [100 "cm"]  [1 "m"] true
       [1 "m"]  [101 "cm"] false)

    (testing-binary-null elm/greater-or-equal #elm/quantity [1])))


;; 12.5. Less
;;
;; The Less operator returns true if the first argument is less than the second
;; argument.
;;
;; For comparisons involving quantities, the dimensions of each quantity must be
;; the same, but not necessarily the unit. For example, units of 'cm' and 'm'
;; are comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For date/time values, the comparison is performed by considering each
;; precision in order, beginning with years (or hours for time values). If the
;; values are the same, comparison proceeds to the next precision; if the first
;; value is less than the second, the result is true; if the first value is
;; greater than the second, the result is false; if one input has a value for
;; the precision and the other does not, the comparison stops and the result is
;; null; if neither input has a value for the precision or the last precision
;; has been reached, the comparison stops and the result is false.
;;
;; If either argument is null, the result is null.
;;
;; The Less operator is defined for the Integer, Decimal, String, Date,
;; DateTime, Time, and Quantity types.
(deftest compile-less-test
  (testing "Integer"
    (are [a b res] (= res (compile-binop elm/less elm/integer a b))
       "1"  "2" true
       "1"  "1" false)

    (testing-binary-null elm/less #elm/integer "1"))

  (testing "Decimal"
    (are [a b res] (= res (compile-binop elm/less elm/decimal a b))
       "1.1"  "2.1" true
       "1.1"  "1.1" false)

    (testing-binary-null elm/less #elm/decimal "1.1"))

  (testing "String"
    (are [a b res] (= res (compile-binop elm/less elm/string a b))
       "a"  "b" true
       "a"  "a" false)

    (testing-binary-null elm/less #elm/string "a"))

  (testing "Date with year precision"
    (are [a b res] (= res (compile-binop elm/less elm/date a b))
       "2012"  "2013" true
       "2013"  "2013" false)

    (testing-binary-null elm/less #elm/date "2013"))

  (testing "Comparing dates with mixed precisions (year and year-month) results in null."
    (are [a b res] (= res (compile-binop elm/less elm/date a b))
       "2013"  "2013-01" nil
       "2013-01"  "2013" nil))

  (testing "Date with full precision"
    (are [a b res] (= res (compile-binop elm/less elm/date a b))
       "2013-06-14"  "2013-06-15" true
       "2013-06-15"  "2013-06-15" false)

    (testing-binary-null elm/less #elm/date "2013-06-15"))

  (testing "Comparing dates with mixed precisions (year-month and full) results in null."
    (are [a b res] (= res (compile-binop elm/less elm/date a b))
       "2013-01"  "2013-01-01" nil
       "2013-01-01"  "2013-01" nil))

  (testing "DateTime with full precision (there is only one precision)"
    (are [a b res] (= res (compile-binop elm/less elm/date-time a b))
       "2013-06-15T11"  "2013-06-15T12" true
       "2013-06-15T12"  "2013-06-15T12" false))

  (testing "Time with full precision (there is only one precision)"
    (are [a b res] (= res (compile-binop elm/less elm/time a b))
       "12:30:14"  "12:30:15" true
       "12:30:15"  "12:30:15" false)

    (testing-binary-null elm/less #elm/time "12:30:15"))

  (testing "Quantity"
    (are [a b res] (= res (compile-binop elm/less elm/quantity a b))
       [1]  [2] true
       [1]  [1] false

       [1 "s"]  [2 "s"] true
       [1 "s"]  [1 "s"] false

       [1 "m"]  [101 "cm"] true
       [1 "m"]  [100 "cm"] false)

    (testing-binary-null elm/less #elm/quantity [1] #elm/quantity [1])))


;; 12.6. LessOrEqual
;;
;; The LessOrEqual operator returns true if the first argument is less than or
;; equal to the second argument.
;;
;; For comparisons involving quantities, the dimensions of each quantity must be
;; the same, but not necessarily the unit. For example, units of 'cm' and 'm'
;; are comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For Date, DateTime, and Time values, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the first value is less than the second, the result is true; if
;; the first value is greater than the second, the result is false; if one input
;; has a value for the precision and the other does not, the comparison stops
;; and the result is null; if neither input has a value for the precision or the
;; last precision has been reached, the comparison stops and the result is true.
;; For the purposes of comparison, seconds and milliseconds are combined as a
;; single precision using a decimal, with decimal comparison semantics.
;;
;; If either argument is null, the result is null.
;;
;; The LessOrEqual operator is defined for the Integer, Decimal, String, Date,
;; DateTime, Time, and Quantity types.
(deftest compile-less-or-equal-test
  (testing "Integer"
    (are [a b res] (= res (compile-binop elm/less-or-equal elm/integer a b))
       "1"  "1" true
       "1"  "2" true
       "2"  "1" false)

    (testing-binary-null elm/less-or-equal #elm/integer "1"))

  (testing "Decimal"
    (are [a b res] (= res (compile-binop elm/less-or-equal elm/decimal a b))
       "1.1"  "1.1" true
       "1.1"  "2.1" true
       "2.1"  "1.1" false)

    (testing-binary-null elm/less-or-equal #elm/decimal "1.1"))

  (testing "Date"
    (are [a b res] (= res (compile-binop elm/less-or-equal elm/date a b))
       "2013-06-14"  "2013-06-15" true
       "2013-06-16"  "2013-06-15" false
       "2013-06-15"  "2013-06-15" true)

    (testing-binary-null elm/less-or-equal #elm/date "2013-06-15"))

  (testing "Mixed Date and DateTime"
    (are [a b res] (= res (compile {} #elm/less-or-equal [a b]))
      #elm/date "2013-06-15" #elm/date-time "2013-06-15T00" nil
      #elm/date-time "2013-06-15T00" #elm/date "2013-06-15" nil))

  (testing "Time"
    (are [a b res] (= res (compile-binop elm/less-or-equal elm/time a b))
       "00:00:00"  "00:00:00" true
       "00:00:00"  "00:00:01" true
       "00:00:01"  "00:00:00" false)

    (testing-binary-null elm/less-or-equal #elm/time "00:00:00"))

  (testing "Quantity"
    (are [a b res] (= res (compile-binop elm/less-or-equal elm/quantity a b))
       [1]  [2] true
       [1]  [1] true
       [2]  [1] false

       [1 "s"]  [2 "s"] true
       [1 "s"]  [1 "s"] true
       [2 "s"]  [1 "s"] false

       [1 "m"]  [101 "cm"] true
       [1 "m"]  [100 "cm"] true
       [101 "cm"]  [1 "m"] false)

    (testing-binary-null elm/less-or-equal #elm/quantity [1] #elm/quantity [1])))


;; 12.7. NotEqual
;;
;; Normalized to Not Equal
(deftest compile-not-equal-test
  (unsupported-binary-operand "NotEqual"))



;; 13. Logical Operators

;; 13.1. And
;;
;; The And operator returns the logical conjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is false, the result is false; if both arguments are true,
;; the result is true; otherwise, the result is null. Note also that ELM does
;; not prescribe short-circuit evaluation.
(def dynamic-resource
  "ELM expression returning the current resource."
  #elm/singleton-from patient-retrieve-elm)


(deftest compile-and-test
  (testing "Static"
    (are [a b res] (= res (compile {} #elm/and [a b]))
      #elm/boolean "true" #elm/boolean "true" true
      #elm/boolean "true" #elm/boolean "false" false
      #elm/boolean "true" {:type "Null"} nil

      #elm/boolean "false" #elm/boolean "true" false
      #elm/boolean "false" #elm/boolean "false" false
      #elm/boolean "false" {:type "Null"} false

      {:type "Null"} #elm/boolean "true" nil
      {:type "Null"} #elm/boolean "false" false
      {:type "Null"} {:type "Null"} nil))

  (let [context {:eval-context "Patient" :node (mem-node-with [])}]
    (testing "Dynamic"
      ;; dynamic-resource will evaluate to true
      (are [a b res] (= res (-eval (compile context #elm/and [a b]) {} true nil))
        #elm/boolean "true" dynamic-resource true
        dynamic-resource #elm/boolean "true" true
        dynamic-resource dynamic-resource true

        dynamic-resource {:type "Null"} nil
        {:type "Null"} dynamic-resource nil)

      ;; dynamic-resource will evaluate to false
      (are [a b res] (= res (-eval (compile context #elm/and [a b]) {} false nil))
        #elm/boolean "true" dynamic-resource false
        dynamic-resource #elm/boolean "true" false
        dynamic-resource dynamic-resource false

        dynamic-resource {:type "Null"} false
        {:type "Null"} dynamic-resource false)

      ;; dynamic-resource will evaluate to nil
      (are [a b res] (= res (-eval (compile context #elm/and [a b]) {} nil nil))
        #elm/boolean "false" dynamic-resource false
        dynamic-resource #elm/boolean "false" false
        #elm/boolean "true" dynamic-resource nil
        dynamic-resource #elm/boolean "true" nil
        dynamic-resource dynamic-resource nil))))


;; 13.2. Implies
;;
;; The Implies operator returns the logical implication of its arguments. Note
;; that this operator is defined using 3-valued logic semantics. This means that
;; if the left operand evaluates to true, this operator returns the boolean
;; evaluation of the right operand. If the left operand evaluates to false, this
;; operator returns true. Otherwise, this operator returns true if the right
;; operand evaluates to true, and null otherwise.
;;
;; Note that implies may use short-circuit evaluation in the case that the first
;; operand evaluates to false.
(deftest compile-implies-test
  (are [a b res] (= res (-eval (compile {} {:type "Or" :operand [{:type "Not" :operand a} b]}) {} nil nil))
    #elm/boolean "true" #elm/boolean "true" true
    #elm/boolean "true" #elm/boolean "false" false
    #elm/boolean "true" {:type "Null"} nil

    #elm/boolean "false" #elm/boolean "true" true
    #elm/boolean "false" #elm/boolean "false" true
    #elm/boolean "false" {:type "Null"} true

    {:type "Null"} #elm/boolean "true" true
    {:type "Null"} #elm/boolean "false" nil
    {:type "Null"} {:type "Null"} nil))


;; 13.3. Not
;;
;; The Not operator returns the logical negation of its argument. If the
;; argument is true, the result is false; if the argument is false, the result
;; is true; otherwise, the result is null.
(deftest compile-not-test
  (testing "Static"
    (are [a res] (= res (compile {} #elm/not a))
      #elm/boolean "true" false
      #elm/boolean "false" true
      {:type "Null"} nil))

  (let [context {:eval-context "Patient" :node (mem-node-with [])}]
    (testing "Dynamic"
      ;; dynamic-resource will evaluate to true
      (are [a res] (= res (-eval (compile context #elm/not a) {} true nil))
        dynamic-resource false)

      ;; dynamic-resource will evaluate to false
      (are [a res] (= res (-eval (compile context #elm/not a) {} false nil))
        dynamic-resource true)

      ;; dynamic-resource will evaluate to nil
      (are [a res] (= res (-eval (compile context #elm/not a) {} nil nil))
        dynamic-resource nil))))


;; 13.4. Or
;;
;; The Or operator returns the logical disjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is true, the result is true; if both arguments are false, the
;; result is false; otherwise, the result is null. Note also that ELM does not
;; prescribe short-circuit evaluation.
(deftest compile-or-test
  (testing "Static"
    (are [a b res] (= res (compile {} #elm/or [a b]))
      #elm/boolean "true" #elm/boolean "true" true
      #elm/boolean "true" #elm/boolean "false" true
      #elm/boolean "true" {:type "Null"} true

      #elm/boolean "false" #elm/boolean "true" true
      #elm/boolean "false" #elm/boolean "false" false
      #elm/boolean "false" {:type "Null"} nil

      {:type "Null"} #elm/boolean "true" true
      {:type "Null"} #elm/boolean "false" nil
      {:type "Null"} {:type "Null"} nil))

  (let [context {:eval-context "Patient" :node (mem-node-with [])}]
    (testing "Dynamic"
      ;; dynamic-resource will evaluate to true
      (are [a b res] (= res (-eval (compile context #elm/or [a b]) {} true nil))
        #elm/boolean "false" dynamic-resource true
        dynamic-resource #elm/boolean "false" true
        dynamic-resource dynamic-resource true

        dynamic-resource {:type "Null"} true
        {:type "Null"} dynamic-resource true)

      ;; dynamic-resource will evaluate to false
      (are [a b res] (= res (-eval (compile context #elm/or [a b]) {} false nil))
        #elm/boolean "false" dynamic-resource false
        dynamic-resource #elm/boolean "false" false
        dynamic-resource dynamic-resource false

        dynamic-resource {:type "Null"} nil
        {:type "Null"} dynamic-resource nil)

      ;; dynamic-resource will evaluate to nil
      (are [a b res] (= res (-eval (compile context #elm/or [a b]) {} nil nil))
        #elm/boolean "true" dynamic-resource true
        dynamic-resource #elm/boolean "true" true
        #elm/boolean "false" dynamic-resource nil
        dynamic-resource #elm/boolean "false" nil
        dynamic-resource dynamic-resource nil))))


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
    (are [a b res] (= res (compile {} #elm/xor [a b]))
      #elm/boolean "true" #elm/boolean "true" false
      #elm/boolean "true" #elm/boolean "false" true
      #elm/boolean "true" {:type "Null"} nil

      #elm/boolean "false" #elm/boolean "true" true
      #elm/boolean "false" #elm/boolean "false" false
      #elm/boolean "false" {:type "Null"} nil

      {:type "Null"} #elm/boolean "true" nil
      {:type "Null"} #elm/boolean "false" nil
      {:type "Null"} {:type "Null"} nil))

  (let [context {:eval-context "Patient":node (mem-node-with [])}]
    (testing "Dynamic"
      ;; dynamic-resource will evaluate to true
      (are [a b res] (= res (-eval (compile context #elm/xor [a b]) {} true nil))
        #elm/boolean "true" dynamic-resource false
        dynamic-resource #elm/boolean "true" false

        #elm/boolean "false" dynamic-resource true
        dynamic-resource #elm/boolean "false" true

        dynamic-resource dynamic-resource false)

      ;; dynamic-resource will evaluate to false
      (are [a b res] (= res (-eval (compile context #elm/xor [a b]) {} false nil))
        #elm/boolean "true" dynamic-resource true
        dynamic-resource #elm/boolean "true" true

        #elm/boolean "false" dynamic-resource false
        dynamic-resource #elm/boolean "false" false

        dynamic-resource dynamic-resource false)

      ;; dynamic-resource will evaluate to nil
      (are [a b res] (= res (-eval (compile context #elm/xor [a b]) {} nil nil))
        #elm/boolean "true" dynamic-resource nil
        dynamic-resource #elm/boolean "true" nil

        #elm/boolean "false" dynamic-resource nil
        dynamic-resource #elm/boolean "false" nil

        {:type "Null"} dynamic-resource nil
        dynamic-resource {:type "Null"} nil

        dynamic-resource dynamic-resource nil))))



;; 14. Nullological Operators

;; 14.1. Null
;;
;; The Null operator returns a null, or missing information marker. To avoid the
;; need to cast this result, the operator is allowed to return a typed null.
(deftest compile-null-test
  (is (nil? (compile {} {:type "Null"}))))


;; 14.2. Coalesce
;;
;; The Coalesce operator returns the first non-null result in a list of
;; arguments. If all arguments evaluate to null the result is null. The static
;; type of the first argument determines the type of the result, and all
;; subsequent arguments must be of that same type.
(deftest compile-coalesce-test
  (are [elm res] (= res (-eval (compile {} {:type "Coalesce" :operand elm}) {} nil nil))
    [] nil
    [{:type "Null"}] nil
    [#elm/boolean "false" #elm/boolean "true"] false
    [{:type "Null"} #elm/integer "1" #elm/integer "2"] 1
    [#elm/integer "2"] 2
    [#elm/list []] nil
    [{:type "Null"} #elm/list [#elm/string "a"]] ["a"]
    [#elm/list [{:type "Null"} #elm/string "a"]] "a"))


;; 14.3. IsFalse
;;
;; The IsFalse operator determines whether or not its argument evaluates to
;; false. If the argument evaluates to false, the result is true; if the
;; argument evaluates to true or null, the result is false.
(deftest compile-is-false-test
  (testing "Static"
    (are [x res] (= res (compile {} #elm/is-false x))
      #elm/boolean "true" false
      #elm/boolean "false" true
      {:type "Null"} false))

  (let [context {:eval-context "Patient":node (mem-node-with [])}]
    (testing "Dynamic"
      ;; dynamic-resource will evaluate to true
      (are [x res] (= res (-eval (compile context #elm/is-false x) {} true nil))
        dynamic-resource false)

      ;; dynamic-resource will evaluate to false
      (are [x res] (= res (-eval (compile context #elm/is-false x) {} false nil))
        dynamic-resource true)

      ;; dynamic-resource will evaluate to nil
      (are [x res] (= res (-eval (compile context #elm/is-false x) {} nil nil))
        dynamic-resource false))))


;; 14.4. IsNull
;;
;; The IsNull operator determines whether or not its argument evaluates to null.
;; If the argument evaluates to null, the result is true; otherwise, the result
;; is false.
(deftest compile-is-null-test
  (testing "Static"
    (are [x res] (= res (compile {} #elm/is-null x))
      #elm/boolean "true" false
      #elm/boolean "false" false
      {:type "Null"} true))

  (let [context {:eval-context "Patient":node (mem-node-with [])}]
    (testing "Dynamic"
      ;; dynamic-resource will evaluate to true
      (are [x res] (= res (-eval (compile context #elm/is-null x) {} true nil))
        dynamic-resource false)

      ;; dynamic-resource will evaluate to false
      (are [x res] (= res (-eval (compile context #elm/is-null x) {} false nil))
        dynamic-resource false)

      ;; dynamic-resource will evaluate to nil
      (are [x res] (= res (-eval (compile context #elm/is-null x) {} nil nil))
        dynamic-resource true))))


;; 14.5. IsTrue
;;
;; The IsTrue operator determines whether or not its argument evaluates to true.
;; If the argument evaluates to true, the result is true; if the argument
;; evaluates to false or null, the result is false.
(deftest compile-is-true-test
  (testing "Static"
    (are [x res] (= res (compile {} #elm/is-true x))
      #elm/boolean "true" true
      #elm/boolean "false" false
      {:type "Null"} false))

  (let [context {:eval-context "Patient":node (mem-node-with [])}]
    (testing "Dynamic"
      ;; dynamic-resource will evaluate to true
      (are [x res] (= res (-eval (compile context #elm/is-true x) {} true nil))
        dynamic-resource true)

      ;; dynamic-resource will evaluate to false
      (are [x res] (= res (-eval (compile context #elm/is-true x) {} false nil))
        dynamic-resource false)

      ;; dynamic-resource will evaluate to nil
      (are [x res] (= res (-eval (compile context #elm/is-true x) {} nil nil))
        dynamic-resource false))))



;; 15. Conditional Operators

;; 15.2. If
;;
;; The If operator evaluates a condition, and returns the then argument if the
;; condition evaluates to true; if the condition evaluates to false or null, the
;; result of the else argument is returned. The static type of the then argument
;; determines the result type of the conditional, and the else argument must be
;; of that same type.
(deftest compile-if-test
  (testing "Static"
    (are [elm res] (= res (compile {} elm))
      #elm/if [#elm/boolean "true" #elm/integer "1" #elm/integer "2"] 1
      #elm/if [#elm/boolean "false" #elm/integer "1" #elm/integer "2"] 2
      #elm/if [{:type "Null"} #elm/integer "1" #elm/integer "2"] 2))

  (let [context {:eval-context "Patient":node (mem-node-with [])}]
    (testing "Dynamic"
      ;; dynamic-resource will evaluate to true
      (are [elm res] (= res (-eval (compile context elm) {} true nil))
        #elm/if [dynamic-resource #elm/integer "1" #elm/integer "2"] 1)

      ;; dynamic-resource will evaluate to false
      (are [elm res] (= res (-eval (compile context elm) {} false nil))
        #elm/if [dynamic-resource #elm/integer "1" #elm/integer "2"] 2)

      ;; dynamic-resource will evaluate to nil
      (are [elm res] (= res (-eval (compile context elm) {} nil nil))
        #elm/if [dynamic-resource #elm/integer "1" #elm/integer "2"] 2))))



;; 16. Arithmetic Operators

;; 16.1. Abs
;;
;; The Abs operator returns the absolute value of its argument.
;;
;; When taking the absolute value of a quantity, the unit is unchanged.
;;
;; If the argument is null, the result is null.
;;
;; The Abs operator is defined for the Integer, Decimal, and Quantity types.
(deftest compile-abs-test
  (are [x res] (= res (-eval (compile {} #elm/abs x) {} nil nil))
    #elm/integer "-1" 1
    #elm/integer "0" 0
    #elm/integer "1" 1

    #elm/decimal "-1" 1M
    #elm/decimal "0" 0M
    #elm/decimal "1" 1M

    #elm/quantity [-1] (quantity 1 "1")
    #elm/quantity [0] (quantity 0 "1")
    #elm/quantity [1] (quantity 1 "1")

    #elm/quantity [-1M] (quantity 1M "1")
    #elm/quantity [0M] (quantity 0M "1")
    #elm/quantity [1M] (quantity 1M "1")

    #elm/quantity [-1 "m"] (quantity 1 "m")
    #elm/quantity [0 "m"] (quantity 0 "m")
    #elm/quantity [1 "m"] (quantity 1 "m")

    #elm/quantity [-1M "m"] (quantity 1M "m")
    #elm/quantity [0M "m"] (quantity 0M "m")
    #elm/quantity [1M "m"] (quantity 1M "m")

    {:type "Null"} nil))


;; 16.2. Add
;;
;; The Add operator performs numeric addition of its arguments.
;;
;; When adding quantities, the dimensions of each quantity must be the same, but
;; not necessarily the unit. For example, units of 'cm' and 'm' can be added,
;; but units of 'cm2' and 'cm' cannot. The unit of the result will be the most
;; granular unit of either input. Attempting to operate on quantities with
;; invalid units will result in a run-time error.
;;
;; The Add operator is defined for the Integer, Decimal, and Quantity types. In
;; addition, a time-valued Quantity can be added to a Date, DateTime or Time
;; using this operator.
;;
;; For Date, DateTime, and Time values, the operator returns the value of the
;; first argument, incremented by the time-valued quantity, respecting variable
;; length periods for calendar years and months.
;;
;; For Date values, the quantity unit must be one of years, months, weeks, or
;; days.
;;
;; For DateTime values, the quantity unit must be one of years, months, weeks,
;; days, hours, minutes, seconds, or milliseconds.
;;
;; For Time values, the quantity unit must be one of hours, minutes, seconds,
;; or milliseconds.
;;
;; Note that as with any date and time operations, temporal units may be
;; specified with either singular, plural, or UCUM units.
;;
;; The operation is performed by converting the time-based quantity to the
;; highest specified granularity in the first argument (truncating any resulting
;; decimal portion) and then adding it to the first argument.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the addition cannot be represented (i.e. arithmetic
;; overflow), the result is null.
(deftest compile-add-test
  (testing "Integer"
    (are [x y res] (= res (compile-binop elm/add elm/integer x y))
       "-1"  "-1" -2
       "-1"  "0" -1
       "-1"  "1" 0
       "1"  "0" 1
       "1"  "1" 2)

    (testing-binary-null elm/add #elm/integer "1"))

  (testing "Adding zero integer to any integer or decimal doesn't change it"
    (satisfies-prop 100
                    (prop/for-all [operand (s/gen (s/or :i :elm/integer :d :elm/decimal))]
                      (let [elm (elm/equal [(elm/add [operand #elm/integer "0"]) operand])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding zero decimal to any decimal doesn't change it"
    (satisfies-prop 100
                    (prop/for-all [operand (s/gen :elm/decimal)]
                      (let [elm (elm/equal [(elm/add [operand #elm/decimal "0"]) operand])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding identical integers equals multiplying the same integer by two"
    (satisfies-prop 100
                    (prop/for-all [integer (s/gen :elm/integer)]
                      (let [elm (elm/equivalent [(elm/add [integer integer])
                                                 (elm/multiply [integer #elm/integer "2"])])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Decimal"
    (testing "Decimal"
      (are [x y res] (= res (compile-binop elm/add elm/decimal x y))
         "-1.1"  "-1.1" -2.2M
         "-1.1"  "0" -1.1M
         "-1.1"  "1.1" 0M
         "1.1"  "0" 1.1M
         "1.1"  "1.1" 2.2M)

      (testing-binary-null elm/add #elm/decimal "1.1"))

    (testing "Mix with integer"
      (are [x y res] (= res (-eval (compile {} #elm/add [x y]) {} nil nil))
        #elm/decimal "1" #elm/integer "1" 2M))

    (testing "Trailing zeros are preserved"
      (are [x y res] (= res (str (-eval (compile {} #elm/add [x y]) {} nil nil)))
        #elm/decimal "1.23" #elm/decimal "1.27" "2.50"))

    (testing "Arithmetic overflow results in nil"
      (are [x y] (nil? (-eval (compile {} #elm/add [x y]) {} nil nil))
        #elm/decimal "99999999999999999999" #elm/decimal "1"
        #elm/decimal "99999999999999999999.99999999" #elm/decimal "1")))

  (testing "Adding identical decimals equals multiplying the same decimal by two"
    (satisfies-prop 100
                    (prop/for-all [decimal (s/gen :elm/decimal)]
                      (let [elm (elm/equal [(elm/add [decimal decimal])
                                            (elm/multiply [decimal #elm/integer "2"])])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding identical decimals and dividing by two results in the same decimal"
    (satisfies-prop 100
                    (prop/for-all [decimal (s/gen :elm/decimal)]
                      (let [elm (elm/equal [(elm/divide [(elm/add [decimal decimal])
                                                         #elm/integer "2"])
                                            decimal])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Time-based quantity"
    (are [x y res] (= res (compile-binop elm/add elm/quantity x y))
       [1 "year"]  [1 "year"] (period 2 0 0)
       [1 "year"]  [1 "month"] (period 1 1 0)
       [1 "year"]  [1 "day"] (period 1 0 (* 24 3600 1000))

       [1 "day"]  [1 "day"] (period 0 0 (* 2 24 3600 1000))
       [1 "day"]  [1 "hour"] (period 0 0 (* 25 3600 1000))

       [1 "year"]  [1.1M "year"] (period 2.1M 0 0)
       [1 "year"]  [13.1M "month"] (period 2 1.1M 0)))

  (testing "UCUM quantity"
    (are [x y res] (= res (compile-binop elm/add elm/quantity x y))
       [1 "m"]  [1 "m"] (quantity 2 "m")
       [1 "m"]  [1 "cm"] (quantity 1.01M "m")))

  (testing "Incompatible UCUM Quantity Subtractions"
    (are [x y] (thrown? UnconvertibleException (compile-binop elm/add elm/quantity x y))
       [1 "cm2"]  [1 "cm"]
       [1 "m"]  [1 "s"]))

  (testing "Adding identical quantities equals multiplying the same quantity with two"
    (satisfies-prop
      100
      (prop/for-all [quantity (gen/such-that :value (s/gen :elm/quantity) 100)]
        (let [elm (elm/equal [(elm/add [quantity quantity])
                              (elm/multiply [quantity #elm/integer "2"])])]
          (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding identical quantities and dividing by two results in the same quantity"
    (satisfies-prop
      100
      (prop/for-all [quantity (gen/such-that :value (s/gen :elm/quantity) 100)]
        (let [elm (elm/equal [(elm/divide [(elm/add [quantity quantity])
                                           #elm/integer "2"])
                              quantity])]
          (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Date + Quantity"
    (are [x y res] (= res (-eval (compile {} #elm/add [x y]) {} nil nil))
      #elm/date "2019" #elm/quantity [1 "year"] (Year/of 2020)
      #elm/date "2019" #elm/quantity [13 "months"] (Year/of 2020)

      #elm/date "2019-01" #elm/quantity [1 "month"] (YearMonth/of 2019 2)
      #elm/date "2019-01" #elm/quantity [12 "month"] (YearMonth/of 2020 1)
      #elm/date "2019-01" #elm/quantity [13 "month"] (YearMonth/of 2020 2)
      #elm/date "2019-01" #elm/quantity [1 "year"] (YearMonth/of 2020 1)

      #elm/date "2019-01-01" #elm/quantity [1 "year"] (LocalDate/of 2020 1 1)
      #elm/date "2012-02-29" #elm/quantity [1 "year"] (LocalDate/of 2013 2 28)
      #elm/date "2019-01-01" #elm/quantity [1 "month"] (LocalDate/of 2019 2 1)
      #elm/date "2019-01-01" #elm/quantity [1 "day"] (LocalDate/of 2019 1 2)))

  (testing "Adding a positive amount of years to a year makes it greater"
    (satisfies-prop 100
                    (prop/for-all [year (s/gen :elm/year)
                                   years (s/gen :elm/pos-years)]
                      (let [elm (elm/greater [(elm/add [year years]) year])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of years to a year-month makes it greater"
    (satisfies-prop 100
                    (prop/for-all [year-month (s/gen :elm/year-month)
                                   years (s/gen :elm/pos-years)]
                      (let [elm (elm/greater [(elm/add [year-month years]) year-month])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of years to a date makes it greater"
    (satisfies-prop 100
                    (prop/for-all [date (s/gen :elm/literal-date)
                                   years (s/gen :elm/pos-years)]
                      (let [elm (elm/greater [(elm/add [date years]) date])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of years to a date-time makes it greater"
    (satisfies-prop 100
                    (prop/for-all [date-time (s/gen :elm/literal-date-time)
                                   years (s/gen :elm/pos-years)]
                      (let [elm (elm/greater [(elm/add [date-time years]) date-time])]
                        (true? (-eval (compile {} elm) {:now now} nil nil))))))

  (testing "Adding a positive amount of months to a year-month makes it greater"
    (satisfies-prop 100
                    (prop/for-all [year-month (s/gen :elm/year-month)
                                   months (s/gen :elm/pos-months)]
                      (let [elm (elm/greater [(elm/add [year-month months]) year-month])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of months to a date makes it greater or lets it equal because a date can be also a year and adding a small amount of months to a year doesn't change it."
    (satisfies-prop 100
                    (prop/for-all [date (s/gen :elm/literal-date)
                                   months (s/gen :elm/pos-months)]
                      (let [elm (elm/greater-or-equal [(elm/add [date months]) date])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of months to a date-time makes it greater or lets it equal because a date-time can be also a year and adding a small amount of months to a year doesn't change it."
    (satisfies-prop 100
                    (prop/for-all [date-time (s/gen :elm/literal-date-time)
                                   months (s/gen :elm/pos-months)]
                      (let [elm (elm/greater-or-equal [(elm/add [date-time months]) date-time])]
                        (true? (-eval (compile {} elm) {:now now} nil nil))))))

  ;; TODO: is that right?
  (testing "Adding a positive amount of days to a year doesn't change it."
    (satisfies-prop 100
                    (prop/for-all [year (s/gen :elm/year)
                                   days (s/gen :elm/pos-days)]
                      (let [elm (elm/equal [(elm/add [year days]) year])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  ;; TODO: is that right?
  (testing "Adding a positive amount of days to a year-month doesn't change it."
    (satisfies-prop 100
                    (prop/for-all [year-month (s/gen :elm/year-month)
                                   days (s/gen :elm/pos-days)]
                      (let [elm (elm/equal [(elm/add [year-month days]) year-month])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of days to a date makes it greater or lets it equal because a date can be also a year or year-month and adding any amount of days to a year or year-month doesn't change it."
    (satisfies-prop 100
                    (prop/for-all [date (s/gen :elm/literal-date)
                                   days (s/gen :elm/pos-days)]
                      (let [elm (elm/greater-or-equal [(elm/add [date days]) date])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding a positive amount of days to a date-time makes it greater or lets it equal because a date-time can be also a year or year-month and adding any amount of days to a year or year-month doesn't change it."
    (satisfies-prop 100
                    (prop/for-all [date-time (s/gen :elm/literal-date-time)
                                   days (s/gen :elm/pos-days)]
                      (let [elm (elm/greater-or-equal [(elm/add [date-time days]) date-time])]
                        (true? (-eval (compile {} elm) {:now now} nil nil))))))

  (testing "DateTime + Quantity"
    (are [x y res] (= res (-eval (compile {} #elm/add [x y]) {} nil nil))
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "year"] (LocalDateTime/of 2020 1 1 0 0 0)
      #elm/date-time "2012-02-29T00" #elm/quantity [1 "year"] (LocalDateTime/of 2013 2 28 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "month"] (LocalDateTime/of 2019 2 1 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "day"] (LocalDateTime/of 2019 1 2 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "hour"] (LocalDateTime/of 2019 1 1 1 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "minute"] (LocalDateTime/of 2019 1 1 0 1 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "second"] (LocalDateTime/of 2019 1 1 0 0 1)))

  (testing "Time + Quantity"
    (are [x y res] (= res (-eval (compile {} #elm/add [x y]) {} nil nil))
      #elm/time "00:00:00" #elm/quantity [1 "hour"] (local-time 1 0 0)
      #elm/time "00:00:00" #elm/quantity [1 "minute"] (local-time 0 1 0)
      #elm/time "00:00:00" #elm/quantity [1 "second"] (local-time 0 0 1))))


;; 16.3. Ceiling
;;
;; The Ceiling operator returns the first integer greater than or equal to the
;; argument.
;;
;; If the argument is null, the result is null.
(deftest compile-ceiling-test
  (are [x res] (= res (compile {} #elm/ceiling x))
    #elm/integer "1" 1
    #elm/decimal "1.1" 2)

  (testing-unary-null elm/ceiling))


;; 16.4. Divide
;;
;; The Divide operator performs numeric division of its arguments. Note that the
;; result type of Divide is Decimal, even if its arguments are of type Integer.
;; For integer division, use the truncated divide operator.
;;
;; For division operations involving quantities, the resulting quantity will
;; have the appropriate unit.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the division cannot be represented, or the right argument is
;; 0, the result is null.
;;
;; The Divide operator is defined for the Decimal and Quantity types.
(deftest compile-divide-test
  (testing "Decimal"
    ;; Convert to string to be able to check for precision
    (are [a b res] (= res (some-> (-eval (compile {} #elm/divide [a b]) {} nil nil) str))
      #elm/decimal "1" #elm/decimal "2" "0.5"
      #elm/decimal "1.1" #elm/decimal "2" "0.55"
      #elm/decimal "10" #elm/decimal "3" "3.33333333"

      #elm/decimal "3" #elm/integer "2" "1.5"

      #elm/decimal "1" #elm/decimal "0" nil
      ; test zero with different precision
      #elm/decimal "1" #elm/decimal "0.0" nil)

    (testing-binary-null elm/divide #elm/decimal "1.1"))

  ;; TODO: fails for -1E-8 because of rounding
  #_(testing "(d * d) / d = d"
      (satisfies-prop 100
                      (prop/for-all [decimal (s/gen :elm/non-zero-decimal)]
                        (let [elm (elm/equal [(elm/divide [(elm/multiply [decimal decimal]) decimal]) decimal])]
                          (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "(d / d) * d = d"
    (satisfies-prop 100
                    (prop/for-all [decimal (s/gen :elm/non-zero-decimal)]
                      (let [elm (elm/equal [(elm/multiply [(elm/divide [decimal decimal]) decimal]) decimal])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "UCUM Quantity"
    (are [a b res] (= res (-eval (compile {} #elm/divide [a b]) {} nil nil))
      #elm/quantity [1M "m"] #elm/integer "2" (quantity 0.5M "m")

      #elm/quantity [1 "m"] #elm/quantity [1 "s"] (quantity 1 "m/s")
      #elm/quantity [1M "m"] #elm/quantity [1M "s"] (quantity 1M "m/s")

      #elm/quantity [12 "cm2"] #elm/quantity [3 "cm"] (quantity 4 "cm"))

    (testing-binary-null elm/divide #elm/quantity [1])))


;; 16.5. Exp
;;
;; The Exp operator returns e raised to the given power.
;;
;; If the argument is null, the result is null.
(deftest compile-exp-test
  (are [x res] (= res (compile {} #elm/exp x))
    #elm/integer "0" 1M
    #elm/decimal "0" 1M)

  (testing-unary-null elm/exp))


;; 16.6. Floor
;;
;; The Floor operator returns the first integer less than or equal to the
;; argument.
;;
;; If the argument is null, the result is null.
(deftest compile-floor-test
  (are [x res] (= res (compile {} #elm/floor x))
    #elm/integer "1" 1
    #elm/decimal "1.1" 1)

  (testing-unary-null elm/floor))


;; 16.7. Log
;;
;; The Log operator computes the logarithm of its first argument, using the
;; second argument as the base.
;;
;; If either argument is null, the result is null.
(deftest compile-log-test
  (are [x base res] (= res (compile {} #elm/log [x base]))
    #elm/integer "16" #elm/integer "2" 4M

    #elm/decimal "100" #elm/decimal "10" 2M
    #elm/decimal "1" #elm/decimal "1" nil

    #elm/integer "0" #elm/integer "2" nil
    #elm/decimal "0" #elm/integer "2" nil)

  (testing-binary-null elm/log #elm/integer "1")

  (testing-binary-null elm/log #elm/decimal "1.1"))


;; 16.8. Ln
;;
;; The Ln operator computes the natural logarithm of its argument.
;;
;; If the argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, the result is null.
(deftest compile-ln-test
  (are [x res] (= res (compile {} #elm/ln x))
    #elm/integer "1" 0M
    #elm/integer "2" 0.69314718M
    #elm/integer "3" 1.09861229M

    #elm/decimal "1" 0M
    #elm/decimal "1.1" 0.09531018M

    #elm/integer "0" nil
    #elm/decimal "0" nil

    #elm/integer "-1" nil
    #elm/decimal "-1" nil)

  (testing-unary-null elm/ln))


;; 16.9. MaxValue
;;
;; The MaxValue operator returns the maximum representable value for the given
;; type.
;;
;; The MaxValue operator is defined for the Integer, Decimal, Date, DateTime,
;; and Time types.
;;
;; For Integer, MaxValue returns the maximum signed 32-bit integer, 2^31 - 1.
;;
;; For Decimal, MaxValue returns the maximum representable Decimal value,
;; (10^28 - 1) / 10^8 (99999999999999999999.99999999).
;;
;; For Date, MaxValue returns the maximum representable Date value,
;; Date(9999, 12, 31).
;;
;; For DateTime, MaxValue returns the maximum representable DateTime value,
;; DateTime(9999, 12, 31, 23, 59, 59, 999).
;;
;; For Time, MaxValue returns the maximum representable Time value,
;; Time(23, 59, 59, 999).
;;
;; For any other type, attempting to invoke MaxValue results in an error.
(deftest compile-max-value-test
  (are [type res] (= res (-eval (compile {} {:type "MaxValue" :valueType type}) {} nil nil))
    "{urn:hl7-org:elm-types:r1}Integer" Integer/MAX_VALUE
    "{urn:hl7-org:elm-types:r1}Decimal" (/ (- 1E28M 1) 1E8M)
    "{urn:hl7-org:elm-types:r1}Date" (LocalDate/of 9999 12 31)
    "{urn:hl7-org:elm-types:r1}DateTime" (LocalDateTime/of 9999 12 31 23 59 59 999000000)
    "{urn:hl7-org:elm-types:r1}Time" (local-time 23 59 59 999)))


;; 16.10. MinValue
;;
;; The MinValue operator returns the minimum representable value for the given
;; type.
;;
;; The MinValue operator is defined for the Integer, Decimal, Date, DateTime,
;; and Time types.
;;
;; For Integer, MinValue returns the minimum signed 32-bit integer, -(2^31).
;;
;; For Decimal, MinValue returns the minimum representable Decimal value,
;; (-10^28 + 1) / 10^8 (-99999999999999999999.99999999).
;;
;; For Date, MinValue returns the minimum representable Date value,
;; Date(1, 1, 1).
;;
;; For DateTime, MinValue returns the minimum representable DateTime value,
;; DateTime(1, 1, 1, 0, 0, 0, 0).
;;
;; For Time, MinValue returns the minimum representable Time value,
;; Time(0, 0, 0, 0).
;;
;; For any other type, attempting to invoke MinValue results in an error.
(deftest compile-min-value-test
  (are [type res] (= res (-eval (compile {} {:type "MinValue" :valueType type}) {} nil nil))
    "{urn:hl7-org:elm-types:r1}Integer" Integer/MIN_VALUE
    "{urn:hl7-org:elm-types:r1}Decimal" (/ (+ -1E28M 1) 1E8M)
    "{urn:hl7-org:elm-types:r1}Date" (LocalDate/of 1 1 1)
    "{urn:hl7-org:elm-types:r1}DateTime" (LocalDateTime/of 1 1 1 0 0 0 0)
    "{urn:hl7-org:elm-types:r1}Time" (local-time 0 0 0 0)))


;; 16.11. Modulo
;;
;; The Modulo operator computes the remainder of the division of its arguments.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the modulo cannot be represented, or the right argument is
;; 0, the result is null.
;;
;; The Modulo operator is defined for the Integer and Decimal types.
(deftest compile-modulo-test
  (testing "Integer"
    (are [x div res] (= res (compile-binop elm/modulo elm/integer x div))
       "1"  "2" 1
       "3"  "2" 1
       "5"  "3" 2)

    (testing-binary-null elm/modulo #elm/integer "1"))

  (testing "Decimal"
    (are [x div res] (= res (compile-binop elm/modulo elm/decimal x div))
       "1"  "2" 1M
       "3"  "2" 1M
       "5"  "3" 2M

       "2.5"  "2" 0.5M)

    (testing-binary-null elm/modulo #elm/decimal "1.1"))

  (testing "Mixed Integer and Decimal"
    (are [x div res] (= res (-eval (compile {} #elm/modulo [x div]) {} nil nil))
      #elm/integer "1" #elm/integer "0" nil
      #elm/decimal "1" #elm/decimal "0" nil)))


;; 16.12. Multiply
;;
;; The Multiply operator performs numeric multiplication of its arguments.
;;
;; For multiplication operations involving quantities, the resulting quantity
;; will have the appropriate unit.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, the result is null.
;;
;; The Multiply operator is defined for the Integer, Decimal and Quantity types.
(deftest compile-multiply-test
  (testing "Integer"
    (are [x y res] (= res (compile-binop elm/multiply elm/integer x y))
      "1" "2" 2
      "2" "2" 4)

    (testing-binary-null elm/multiply #elm/integer "1"))

  (testing "Decimal"
    (testing "Decimal"
      (are [x y res] (= res (compile-binop elm/multiply elm/decimal x y))
        "1" "2" 2M
        "1.23456" "1.23456" 1.52413839M)

      (testing-binary-null elm/multiply #elm/decimal "1.1"))

    (testing "Arithmetic overflow results in nil"
      (are [x y] (nil? (compile-binop elm/multiply elm/decimal x y))
        "99999999999999999999" "2"
        "99999999999999999999.99999999" "2")))

  (testing "Quantity"
    (are [x y res] (= res (-eval (compile {} #elm/multiply [x y]) {} nil nil))
      #elm/quantity [1 "m"] #elm/integer "2" (quantity 2 "m")
      #elm/quantity [1 "m"] #elm/quantity [2 "m"] (quantity 2 "m2"))

    (testing-binary-null elm/multiply #elm/quantity [1])))


;; 16.13. Negate
;;
;; The Negate operator returns the negative of its argument.
;;
;; When negating quantities, the unit is unchanged.
;;
;; If the argument is null, the result is null.
;;
;; The Negate operator is defined for the Integer, Decimal, and Quantity types.
(deftest compile-negate-test
  (testing "Integer"
    (are [x res] (= res (compile {} #elm/negate #elm/integer x))
      "1" -1))

  (testing "Decimal"
    (are [x res] (= res (compile {} #elm/negate #elm/decimal x))
      "1" -1M))

  (testing "Quantity"
    (are [x res] (= res (compile {} #elm/negate x))
      #elm/quantity [1] (quantity -1 "1")
      #elm/quantity [1M] (quantity -1M "1")
      #elm/quantity [1 "m"] (quantity -1 "m")
      #elm/quantity [1M "m"] (quantity -1M "m")))

  (testing-unary-null elm/negate))


;; 16.14. Power
;;
;; The Power operator raises the first argument to the power given by the
;; second argument.
;;
;; When invoked with mixed Integer and Decimal arguments, the Integer argument
;; will be implicitly converted to Decimal.
;;
;; If either argument is null, the result is null.
(deftest compile-power-test
  (testing "Integer"
    (are [a b res] (= res (compile-binop elm/power elm/integer a b))
      "10" "2" 100
      "2" "-2" 0.25M)

    (testing-binary-null elm/power #elm/integer "1"))

  (testing "Decimal"
    (are [a b res] (= res (compile-binop elm/power elm/decimal a b))
      "2.5" "2" 6.25M
      "10" "2" 100M
      "4" "0.5" 2M)

    (testing-binary-null elm/power #elm/decimal "1.1"))

  (testing "Mixed"
    (are [a b res] (= res (compile {} #elm/power [a b]))
      #elm/decimal "2.5" #elm/integer "2" 6.25M
      #elm/decimal "10" #elm/integer "2" 100M
      #elm/decimal "10" #elm/integer "2" 100M)))


;; 16.15. Predecessor
;;
;; The Predecessor operator returns the predecessor of the argument. For
;; example, the predecessor of 2 is 1. If the argument is already the minimum
;; value for the type, a run-time error is thrown.
;;
;; The Predecessor operator is defined for the Integer, Decimal, Quantity, Date,
;; DateTime, and Time types.
;;
;; For Integer, Predecessor is equivalent to subtracting 1.
;;
;; For Decimal, Predecessor is equivalent to subtracting the minimum precision
;; value for the Decimal type, or 10^-08.
;;
;; For Date, DateTime, and Time values, Predecessor is equivalent to
;; subtracting a time-unit quantity for the lowest specified precision of the
;; value. For example, if the DateTime is fully specified, Predecessor is
;; equivalent to subtracting 1 millisecond; if the DateTime is specified to the
;; second, Predecessor is equivalent to subtracting one second, etc.
;;
;; For Quantity values, the predecessor is equivalent to subtracting 1 if the
;; quantity is an integer, and the minimum precision value for the Decimal type
;; if the quantity is a decimal. The units are unchanged.
;;
;; If the argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, the result is null.
(deftest compile-predecessor-test
  (testing "Integer"
    (are [x res] (= res (compile {} #elm/predecessor x))
      #elm/integer "0" -1))

  (testing "Decimal"
    (are [x res] (= res (compile {} #elm/predecessor x))
      #elm/decimal "0" -1E-8M))

  (testing "Date"
    (are [x res] (= res (compile {} #elm/predecessor x))
      #elm/date "2019" (Year/of 2018)
      #elm/date "2019-01" (YearMonth/of 2018 12)
      #elm/date "2019-01-01" (LocalDate/of 2018 12 31)))

  (testing "DateTime"
    (are [x res] (= res (compile {} #elm/predecessor x))
      #elm/date-time "2019-01-01T00" (LocalDateTime/of 2018 12 31 23 59 59 999000000)))

  (testing "Time"
    (are [x res] (= res (compile {} #elm/predecessor x))
      #elm/time "12:00" (local-time 11 59)))

  (testing "Quantity"
    (are [x res] (= res (compile {} #elm/predecessor x))
      #elm/quantity [0 "m"] (quantity -1 "m")
      #elm/quantity [0M "m"] (quantity -1E-8M "m")))

  (testing-unary-null elm/predecessor)

  (testing "throws error if the argument is already the minimum value"
    (are [x] (thrown-anom? ::anom/incorrect (compile {} #elm/predecessor x))
      (elm/decimal (str decimal/min))
      #elm/date "0001"
      #elm/date "0001-01"
      #elm/date "0001-01-01"
      #elm/time "00:00:00.0"
      #elm/date-time "0001-01-01T00:00:00.0"
      #elm/quantity [decimal/min])))


;; 16.16. Round
;;
;; The Round operator returns the nearest integer to its argument. The semantics
;; of round are defined as a traditional round, meaning that a decimal value of
;; 0.5 or higher will round to 1.
;;
;; If the argument is null, the result is null.
;;
;; Precision determines the decimal place at which the rounding will occur. If
;; precision is not specified or null, 0 is assumed.
(deftest compile-round-test
  (let [context {:eval-context "Patient":node (mem-node-with [])}]
    (testing "Without precision"
      (testing "Static"
        (are [x res] (= res (compile {} #elm/round [x]))
          #elm/integer "1" 1M
          #elm/decimal "1" 1M
          #elm/decimal "0.5" 1M
          #elm/decimal "0.4" 0M
          #elm/decimal "-0.4" 0M
          #elm/decimal "-0.5" -1M
          #elm/decimal "-0.6" -1M
          #elm/decimal "-1.1" -1M
          #elm/decimal "-1.5" -2M
          #elm/decimal "-1.6" -2M
          {:type "Null"} nil))

      (testing "Dynamic Null"
        (let [elm #elm/round [#elm/singleton-from patient-retrieve-elm]
              expr (compile context elm)]
          (is (nil? (-eval expr {} nil nil))))))

    (testing "With precision"
      (testing "Static"
        (are [x precision res] (= res (compile {} #elm/round [x precision]))
          #elm/decimal "3.14159" #elm/integer "3" 3.142M
          {:type "Null"} #elm/integer "3" nil))

      (testing "Dynamic Null"
        (let [elm #elm/round [#elm/singleton-from patient-retrieve-elm #elm/integer "3"]
              expr (compile context elm)]
          (is (nil? (-eval expr {} nil nil))))))))


;; 16.17. Subtract
;;
;; The Subtract operator performs numeric subtraction of its arguments.
;;
;; When subtracting quantities, the dimensions of each quantity must be the same,
;; but not necessarily the unit. For example, units of 'cm' and 'm' can be
;; subtracted, but units of 'cm2' and 'cm' cannot. The unit of the result will
;; be the most granular unit of either input. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; The Subtract operator is defined for the Integer, Decimal, and Quantity types.
;; In addition, a time-valued Quantity can be subtracted from a Date, DateTime,
;; or Time using this operator.
;;
;; For Date, DateTime, Time values, the operator returns the value of the given
;; date/time, decremented by the time-valued quantity, respecting variable
;; length periods for calendar years and months.
;;
;; For Date values, the quantity unit must be one of years, months, weeks, or
;; days.
;;
;; For DateTime values, the quantity unit must be one of years, months, weeks,
;; days, hours, minutes, seconds, or milliseconds.
;;
;; For Time values, the quantity unit must be one of hours, minutes, seconds, or
;; milliseconds.
;;
;; The operation is performed by converting the time-based quantity to the
;; highest specified granularity in the date/time value (truncating any
;; resulting decimal portion) and then adding it to the date/time value.
;;
;; If either argument is null, the result is null.
(deftest compile-subtract-test
  (testing "Integer"
    (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
      #elm/integer "-1" #elm/integer "-1" 0
      #elm/integer "-1" #elm/integer "0" -1
      #elm/integer "1" #elm/integer "1" 0
      #elm/integer "1" #elm/integer "0" 1
      #elm/integer "1" #elm/integer "-1" 2

      {:type "Null"} #elm/integer "1" nil
      #elm/integer "1" {:type "Null"} nil))

  (testing "Subtracting identical integers results in zero"
    (satisfies-prop 100
                    (prop/for-all [integer (s/gen :elm/integer)]
                      (zero? (-eval (compile {} (elm/subtract [integer integer])) {} nil nil)))))

  (testing "Decimal"
    (testing "Decimal"
      (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
        #elm/decimal "-1" #elm/decimal "-1" 0M
        #elm/decimal "-1" #elm/decimal "0" -1M
        #elm/decimal "1" #elm/decimal "1" 0M
        #elm/decimal "1" #elm/decimal "0" 1M
        #elm/decimal "1" #elm/decimal "-1" 2M

        {:type "Null"} #elm/decimal "1.1" nil
        #elm/decimal "1.1" {:type "Null"} nil))

    (testing "Mix with integer"
      (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
        #elm/decimal "1" #elm/integer "1" 0M))

    (testing "Arithmetic overflow results in nil"
      (are [x y] (nil? (-eval (compile {} (elm/subtract [x y])) {} nil nil))
        #elm/decimal "-99999999999999999999" #elm/decimal "1"
        #elm/decimal "-99999999999999999999.99999999" #elm/decimal "1")))

  (testing "Subtracting identical decimals results in zero"
    (satisfies-prop 100
                    (prop/for-all [decimal (s/gen :elm/decimal)]
                      (zero? (-eval (compile {} (elm/subtract [decimal decimal])) {} nil nil)))))

  (testing "Time-based quantity"
    (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
      #elm/quantity [1 "year"] #elm/quantity [1 "year"] (period 0 0 0)
      #elm/quantity [1 "year"] #elm/quantity [1 "month"] (period 0 11 0)
      #elm/quantity [1 "year"] #elm/quantity [1 "day"] (period 1 0 (- (* 24 3600 1000)))

      #elm/quantity [1 "day"] #elm/quantity [1 "day"] (period 0 0 0)
      #elm/quantity [1 "day"] #elm/quantity [1 "hour"] (period 0 0 (* 23 3600 1000))

      #elm/quantity [1 "year"] #elm/quantity [1.1M "year"] (period -0.1M 0 0)
      #elm/quantity [1 "year"] #elm/quantity [13.1M "month"] (period 0 -1.1M 0)))

  (testing "UCUM quantity"
    (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
      #elm/quantity [1 "m"] #elm/quantity [1 "m"] (quantity 0 "m")
      #elm/quantity [1 "m"] #elm/quantity [1 "cm"] (quantity 0.99 "m")))

  (testing "Incompatible UCUM Quantity Subtractions"
    (are [a b] (thrown? UnconvertibleException (-eval (compile {} (elm/subtract [a b])) {} nil nil))
      #elm/quantity [1 "cm2"] #elm/quantity [1 "cm"]
      #elm/quantity [1 "m"] #elm/quantity [1 "s"]))

  (testing "Subtracting identical quantities results in zero"
    (satisfies-prop
      100
      (prop/for-all [quantity (gen/such-that :value (s/gen :elm/quantity) 100)]
        ;; Can't test for zero because can't extract value from quantity
        ;; so use negate trick
        (let [elm (elm/equal [(elm/negate (elm/subtract [quantity quantity]))
                              (elm/subtract [quantity quantity])])]
          (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Date - Quantity"
    (are [x y res] (= res (compile {} #elm/subtract [x y]))
      #elm/date "2019" #elm/quantity [1 "year"] (Year/of 2018)
      #elm/date "2019" #elm/quantity [13 "months"] (Year/of 2018)

      #elm/date "2019-01" #elm/quantity [1 "month"] (YearMonth/of 2018 12)
      #elm/date "2019-01" #elm/quantity [12 "month"] (YearMonth/of 2018 1)
      #elm/date "2019-01" #elm/quantity [13 "month"] (YearMonth/of 2017 12)
      #elm/date "2019-01" #elm/quantity [1 "year"] (YearMonth/of 2018 1)

      #elm/date "2019-01-01" #elm/quantity [1 "year"] (LocalDate/of 2018 1 1)
      #elm/date "2012-02-29" #elm/quantity [1 "year"] (LocalDate/of 2011 2 28)
      #elm/date "2019-01-01" #elm/quantity [1 "month"] (LocalDate/of 2018 12 1)
      #elm/date "2019-01-01" #elm/quantity [1 "day"] (LocalDate/of 2018 12 31)))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of years from a year makes it smaller"
      (satisfies-prop 100
                      (prop/for-all [year (s/gen :elm/year)
                                     years (s/gen :elm/pos-years)]
                        (let [elm (elm/less [(elm/subtract [year years]) year])]
                          (true? (-eval (compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of years from a year-month makes it smaller"
      (satisfies-prop 100
                      (prop/for-all [year-month (s/gen :elm/year-month)
                                     years (s/gen :elm/pos-years)]
                        (let [elm (elm/less [(elm/subtract [year-month years]) year-month])]
                          (true? (-eval (compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of years from a date makes it smaller"
      (satisfies-prop 100
                      (prop/for-all [date (s/gen :elm/literal-date)
                                     years (s/gen :elm/pos-years)]
                        (let [elm (elm/less [(elm/subtract [date years]) date])]
                          (true? (-eval (compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of months from a year-month makes it smaller"
      (satisfies-prop 100
                      (prop/for-all [year-month (s/gen :elm/year-month)
                                     months (s/gen :elm/pos-months)]
                        (let [elm (elm/less [(elm/subtract [year-month months]) year-month])]
                          (true? (-eval (compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of months from a date makes it smaller or lets it equal because a date can be also a year and subtracting a small amount of months from a year doesn't change it."
      (satisfies-prop 100
                      (prop/for-all [date (s/gen :elm/literal-date)
                                     months (s/gen :elm/pos-months)]
                        (let [elm (elm/less-or-equal [(elm/subtract [date months]) date])]
                          (true? (-eval (compile {} elm) {} nil nil))))))

  ;; TODO: find a solution to avoid overflow
  #_(testing "Subtracting a positive amount of days from a date makes it smaller or lets it equal because a date can be also a year or year-month and subtracting any amount of days from a year or year-month doesn't change it."
      (satisfies-prop 100
                      (prop/for-all [date (s/gen :elm/literal-date)
                                     days (s/gen :elm/pos-days)]
                        (let [elm (elm/less-or-equal [(elm/subtract [date days]) date])]
                          (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "DateTime - Quantity"
    (are [x y res] (= res (compile {} #elm/subtract [x y]))
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "year"] (LocalDateTime/of 2018 1 1 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "month"] (LocalDateTime/of 2018 12 1 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "day"] (LocalDateTime/of 2018 12 31 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "hour"] (LocalDateTime/of 2018 12 31 23 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "minute"] (LocalDateTime/of 2018 12 31 23 59 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "second"] (LocalDateTime/of 2018 12 31 23 59 59)))

  (testing "Time - Quantity"
    (are [x y res] (= res (compile {} #elm/subtract [x y]))
      #elm/time "00:00:00" #elm/quantity [1 "hour"] (local-time 23 0 0)
      #elm/time "00:00:00" #elm/quantity [1 "minute"] (local-time 23 59 0)
      #elm/time "00:00:00" #elm/quantity [1 "second"] (local-time 23 59 59))))


;; 16.18. Successor
;;
;; The Successor operator returns the successor of the argument. For example,
;; the successor of 1 is 2. If the argument is already the maximum value for the
;; type, a run-time error is thrown.
;;
;; The Successor operator is defined for the Integer, Decimal, Date, DateTime,
;; and Time types.
;;
;; For Integer, Successor is equivalent to adding 1.
;;
;; For Decimal, Successor is equivalent to adding the minimum precision value
;; for the Decimal type, or 10^-08.
;;
;; For Date, DateTime, and Time values, Successor is equivalent to adding a
;; time-unit quantity for the lowest specified precision of the value. For
;; example, if the DateTime is fully specified, Successor is equivalent to
;; adding 1 millisecond; if the DateTime is specified to the second, Successor
;; is equivalent to adding one second, etc.
;;
;; If the argument is null, the result is null.
(deftest compile-successor-test
  (are [x res] (= res (-eval (compile {} (elm/successor x)) {} nil nil))
    #elm/integer "0" 1
    #elm/decimal "0" 1E-8M
    #elm/date "2019" (Year/of 2020)
    #elm/date "2019-01" (YearMonth/of 2019 2)
    #elm/date "2019-01-01" (LocalDate/of 2019 1 2)
    #elm/date-time "2019-01-01T00" (LocalDateTime/of 2019 1 1 0 0 0 1000000)
    #elm/time "00:00:00" (local-time 0 0 1)
    #elm/quantity [0 "m"] (quantity 1 "m")
    #elm/quantity [0M "m"] (quantity 1E-8M "m")
    {:type "Null"} nil)

  (are [x] (thrown? Exception (-eval (compile {} (elm/successor x)) {} nil nil))
    (elm/decimal (str decimal/max))
    #elm/date "9999"
    #elm/date "9999-12"
    #elm/date "9999-12-31"
    #elm/time "23:59:59.999"
    #elm/date-time "9999-12-31T23:59:59.999"))


;; 16.19. Truncate
;;
;; The Truncate operator returns the integer component of its argument.
;;
;; If the argument is null, the result is null.
(deftest compile-truncate-test
  (are [x res] (= res (-eval (compile {} (elm/truncate x)) {} nil nil))
    #elm/integer "1" 1
    #elm/decimal "1.1" 1
    {:type "Null"} nil))


;; 16.20. TruncatedDivide
;;
;; The TruncatedDivide operator performs integer division of its arguments.
;;
;; If either argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, or the right argument
;; is 0, the result is null.
;;
;; The TruncatedDivide operator is defined for the Integer and Decimal types.
(deftest compile-truncated-divide-test
  (are [num div res] (= res (-eval (compile {} (elm/truncated-divide [num div])) {} nil nil))
    #elm/integer "1" #elm/integer "2" 0
    #elm/integer "2" #elm/integer "2" 1

    #elm/decimal "4.14" #elm/decimal "2.06" 2M

    #elm/integer "1" #elm/integer "0" nil

    {:type "Null"} #elm/integer "1" nil
    #elm/integer "1" {:type "Null"} nil))


;; 17. String Operators

;; 17.1. Combine
;;
;; The Combine operator combines a list of strings, optionally separating each
;; string with the given separator.
;;
;; If either argument is null, or any element in the source list of strings is
;; null, the result is null.
;;
;; TODO: This definition is inconsistent with the CQL definition https://cql.hl7.org/2019May/09-b-cqlreference.html#combine
(deftest compile-combine-test
  (testing "Without separator"
    (are [src res] (= res (-eval (compile {} {:type "Combine" :source src}) {} nil nil))
      #elm/list [#elm/string "a"] "a"
      #elm/list [#elm/string "a" #elm/string "b"] "ab"

      #elm/list [] nil
      #elm/list [#elm/string "a" {:type "Null"}] nil
      #elm/list [{:type "Null"}] nil
      {:type "Null"} nil))

  (testing "With separator"
    (are [src res] (= res (-eval (compile {} {:type "Combine" :source src :separator #elm/string " "}) {} nil nil))
      #elm/list [#elm/string "a"] "a"
      #elm/list [#elm/string "a" #elm/string "b"] "a b"

      #elm/list [] nil
      #elm/list [#elm/string "a" {:type "Null"}] nil
      #elm/list [{:type "Null"}] nil
      {:type "Null"} nil)))


;; 17.2. Concatenate
;;
;; The Concatenate operator performs string concatenation of its arguments.
;;
;; If any argument is null, the result is null.
(deftest compile-concatenate-test
  (are [args res] (= res (-eval (compile {} {:type "Concatenate" :operand args}) {} nil nil))
    [#elm/string "a"] "a"
    [#elm/string "a" #elm/string "b"] "ab"

    [#elm/string "a" {:type "Null"}] nil
    [{:type "Null"}] nil))


;; 17.3. EndsWith
;;
;; The EndsWith operator returns true if the given string ends with the given
;; suffix.
;;
;; If the suffix is the empty string, the result is true.
;;
;; If either argument is null, the result is null.
(deftest compile-ends-with-test
  (are [s suffix res] (= res (-eval (compile {} {:type "EndsWith" :operand [s suffix]}) {} nil nil))
    #elm/string "a" #elm/string "a" true
    #elm/string "ab" #elm/string "b" true

    #elm/string "a" #elm/string "b" false
    #elm/string "ba" #elm/string "b" false

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


;; 17.4. Equal
;;
;; See 12.1. Equal


;; 17.5. Equivalent
;;
;; See 12.2. Equivalent


;; 17.6. Indexer
;;
;; The Indexer operator returns the indexth element in a string or list.
;;
;; Indexes in strings and lists are defined to be 0-based.
;;
;; If the index is less than 0 or greater than the length of the string or list
;; being indexed, the result is null.
;;
;; If either argument is null, the result is null.
(deftest compile-indexer-test
  (testing "String"
    (are [x i res] (= res (-eval (compile {} {:type "Indexer" :operand [x i]}) {} nil nil))
      #elm/string "a" #elm/integer "0" "a"
      #elm/string "ab" #elm/integer "1" "b"

      #elm/string "" #elm/integer "-1" nil
      #elm/string "" #elm/integer "0" nil
      #elm/string "a" #elm/integer "1" nil

      #elm/string "" {:type "Null"} nil
      {:type "Null"} #elm/integer "0" nil))

  (testing "List"
    (are [x i res] (= res (-eval (compile {} {:type "Indexer" :operand [x i]}) {} nil nil))
      #elm/list [#elm/integer "1"] #elm/integer "0" 1
      #elm/list [#elm/integer "1" #elm/integer "2"] #elm/integer "1" 2

      #elm/list [] #elm/integer "-1" nil
      #elm/list [] #elm/integer "0" nil
      #elm/list [#elm/integer "1"] #elm/integer "1" nil

      #elm/list [] {:type "Null"} nil
      {:type "Null"} #elm/integer "0" nil)))


;; 17.7. LastPositionOf
;;
;; The LastPositionOf operator returns the 0-based index of the beginning of the
;; last appearance of the given pattern in the given string.
;;
;; If the pattern is not found, the result is -1.
;;
;; If either argument is null, the result is null.
(deftest compile-last-position-of-test
  (are [pattern s res] (= res (-eval (compile {} {:type "LastPositionOf" :pattern pattern :string s}) {} nil nil))
    #elm/string "a" #elm/string "a" 0
    #elm/string "a" #elm/string "aa" 1

    #elm/string "a" #elm/string "b" -1

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


;; 17.8. Length
;;
;; The Length operator returns the length of its argument.
;;
;; For strings, the length is the number of characters in the string.
;;
;; For lists, the length is the number of elements in the list.
;;
;; If the argument is null, the result is 0.
(deftest compile-length-test
  (are [x res] (= res (-eval (compile {} {:type "Length" :operand x}) {} nil nil))
    #elm/string "" 0
    #elm/string "a" 1
    #elm/list [] 0
    #elm/list [#elm/integer "1"] 1

    {:type "Null"} 0))


;; 17.9. Lower
;;
;; The Lower operator returns the given string with all characters converted to
;; their lowercase equivalents.
;;
;; Note that the definition of lowercase for a given character is a
;; locale-dependent determination, and is not specified by CQL. Implementations
;; are expected to provide appropriate and consistent handling of locale for
;; their environment.
;;
;; If the argument is null, the result is null.
(deftest compile-lower-test
  (are [s res] (= res (-eval (compile {} {:type "Lower" :operand s}) {} nil nil))
    #elm/string "" ""
    #elm/string "A" "a"

    {:type "Null"} nil))


;; 17.10. Matches
;;
;; The Matches operator returns true if the given string matches the given
;; regular expression pattern. Regular expressions should function consistently,
;; regardless of any culture- and locale-specific settings in the environment,
;; should be case-sensitive, use single line mode, and allow Unicode characters.
;;
;; If either argument is null, the result is null.
;;
;; Platforms will typically use native regular expression implementations. These
;; are typically fairly similar, but there will always be small differences. As
;; such, CQL does not prescribe a particular dialect, but recommends the use of
;; the PCRE dialect.
(deftest compile-matches-test
  (are [s pattern res] (= res (-eval (compile {} {:type "Matches" :operand [s pattern]}) {} nil nil))
    #elm/string "a" #elm/string "a" true

    #elm/string "a" #elm/string "\\d" false

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


;; 17.11. NotEqual
;;
;; See 12.7. NotEqual


;; 17.12. PositionOf
;;
;; The PositionOf operator returns the 0-based index of the beginning given
;; pattern in the given string.
;;
;; If the pattern is not found, the result is -1.
;;
;; If either argument is null, the result is null.
(deftest compile-position-of-test
  (are [pattern s res] (= res (-eval (compile {} {:type "PositionOf" :pattern pattern :string s}) {} nil nil))
    #elm/string "a" #elm/string "a" 0
    #elm/string "a" #elm/string "aa" 0

    #elm/string "a" #elm/string "b" -1

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


;; 17.13. ReplaceMatches
;;
;; The ReplaceMatches operator matches the given string using the regular
;; expression pattern, replacing each match with the given substitution. The
;; substitution string may refer to identified match groups in the regular
;; expression. Regular expressions should function consistently, regardless of
;; any culture- and locale-specific settings in the environment, should be
;; case-sensitive, use single line mode and allow Unicode characters.
;;
;; If any argument is null, the result is null.
;;
;; Platforms will typically use native regular expression implementations. These
;; are typically fairly similar, but there will always be small differences. As
;; such, CQL does not prescribe a particular dialect, but recommends the use of
;; the PCRE dialect.
(deftest compile-replace-matches-test
  (are [s pattern substitution res] (= res (-eval (compile {} {:type "ReplaceMatches" :operand [s pattern substitution]}) {} nil nil))
    #elm/string "a" #elm/string "a" #elm/string "b" "b"

    {:type "Null"} #elm/string "a" {:type "Null"} nil
    #elm/string "a" {:type "Null"} {:type "Null"} nil
    {:type "Null"} {:type "Null"} {:type "Null"} nil))


;; 17.14. Split
;;
;; The Split operator splits a string into a list of strings using a separator.
;;
;; If the stringToSplit argument is null, the result is null.
;;
;; If the stringToSplit argument does not contain any appearances of the
;; separator, the result is a list of strings containing one element that is the
;; value of the stringToSplit argument.
(deftest compile-split-test
  (testing "Without separator"
    (are [s res] (= res (-eval (compile {} {:type "Split" :stringToSplit s}) {} nil nil))
      #elm/string "" [""]
      #elm/string "a" ["a"]

      {:type "Null"} nil))

  (testing "With separator"
    (are [s separator res] (= res (-eval (compile {} {:type "Split" :stringToSplit s :separator separator}) {} nil nil))
      #elm/string "" #elm/string "," [""]
      #elm/string "a,b" #elm/string "," ["a" "b"]
      #elm/string "a,,b" #elm/string "," ["a" "" "b"]

      {:type "Null"} #elm/string "," nil
      #elm/string "a" {:type "Null"} ["a"]
      {:type "Null"} {:type "Null"} nil)))


;; 17.15. SplitOnMatches
;;
;; The SplitOnMatches operator splits a string into a list of strings using
;; matches of a regex pattern.
;;
;; The separatorPattern argument is a regex pattern, following the same
;; semantics as the Matches operator.
;;
;; If the stringToSplit argument is null, the result is null.
;;
;; If the stringToSplit argument does not contain any appearances of the
;; separator pattern, the result is a list of strings containing one element
;; that is the input value of the stringToSplit argument.


;; 17.16. StartsWith
;;
;; The StartsWith operator returns true if the given string starts with the
;; given prefix.
;;
;; If the prefix is the empty string, the result is true.
;;
;; If either argument is null, the result is null.
(deftest compile-starts-with-test
  (are [s prefix res] (= res (-eval (compile {} {:type "StartsWith" :operand [s prefix]}) {} nil nil))
    #elm/string "a" #elm/string "a" true
    #elm/string "ba" #elm/string "b" true

    #elm/string "a" #elm/string "b" false
    #elm/string "ab" #elm/string "b" false

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil
    {:type "Null"} {:type "Null"} nil))


;; 17.17. Substring
;;
;; The Substring operator returns the string within stringToSub, starting at the
;; 0-based index startIndex, and consisting of length characters.
;;
;; If length is ommitted, the substring returned starts at startIndex and
;; continues to the end of stringToSub.
;;
;; If stringToSub or startIndex is null, or startIndex is out of range, the
;; result is null.
;;
;; TODO: what todo if the length is out of range?
(deftest compile-substring-test
  (testing "Without length"
    (are [s start-index res] (= res (-eval (compile {} {:type "Substring" :stringToSub s :startIndex start-index}) {} nil nil))
      #elm/string "ab" #elm/integer "1" "b"

      #elm/string "a" #elm/integer "-1" nil
      #elm/string "a" #elm/integer "1" nil
      {:type "Null"} #elm/integer "0" nil
      #elm/string "a" {:type "Null"} nil
      {:type "Null"} {:type "Null"} nil))

  (testing "With length"
    (are [s start-index length res] (= res (-eval (compile {} {:type "Substring" :stringToSub s :startIndex start-index :length length}) {} nil nil))
      #elm/string "a" #elm/integer "0" #elm/integer "1" "a"
      #elm/string "a" #elm/integer "0" #elm/integer "2" "a"
      #elm/string "abc" #elm/integer "1" #elm/integer "1" "b"

      #elm/string "a" #elm/integer "-1" #elm/integer "0" nil
      #elm/string "a" #elm/integer "2" #elm/integer "0" nil
      {:type "Null"} #elm/integer "0" #elm/integer "0" nil
      #elm/string "a" {:type "Null"} #elm/integer "0" nil
      {:type "Null"} {:type "Null"} #elm/integer "0" nil)))


;; 17.18. Upper
;;
;; The Upper operator returns the given string with all characters converted to
;; their upper case equivalents.
;;
;; Note that the definition of uppercase for a given character is a
;; locale-dependent determination, and is not specified by CQL. Implementations
;; are expected to provide appropriate and consistent handling of locale for
;; their environment.
;;
;; If the argument is null, the result is null.
(deftest compile-upper-test
  (are [s res] (= res (-eval (compile {} {:type "Upper" :operand s}) {} nil nil))
    #elm/string "" ""
    #elm/string "a" "A"

    {:type "Null"} nil))



;; 18. Date and Time Operators

;; 18.1 Add
;;
;; See 16.2. Add


;; 18.2 After
;;
;; See 19.2. After


;; 18.3 Before
;;
;; See 19.3. Before


;; 18.4. Equal
;;
;; See 12.1. Equal


;; 18.5. Equivalent
;;
;; See 12.2. Equivalent


;; 18.6. Date
;;
;; The Date operator constructs a date value from the given components.
;;
;; At least one component must be specified, and no component may be specified
;; at a precision below an unspecified precision. For example, month may be
;; null, but if it is, day must be null as well.
(deftest compile-date-test
  (let [context {:eval-context "Patient":node (mem-node-with [])}]
    (testing "Static Null year"
      (is (nil? (compile {} #elm/date [{:type "null"}]))))

    (testing "Static year"
      (is (= (Year/of 2019) (compile {} #elm/date "2019"))))

    (testing "Static year over 10.000"
      (is (thrown-anom? ::anom/incorrect (compile {} #elm/date "10001"))))

    (testing "Dynamic Null year"
      (let [elm #elm/date [#elm/singleton-from patient-retrieve-elm]
            expr (compile context elm)]
        (is (nil? (-eval expr {} nil nil)))))

    (testing "Dynamic year"
      (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
        #elm/date [#elm/add [#elm/integer "2018" #elm/integer "1"]]
        (Year/of 2019)))

    (testing "Dynamic Null month"
      (let [elm #elm/date [#elm/integer "2018"
                           #elm/singleton-from patient-retrieve-elm]
            expr (compile context elm)]
        (is (= (Year/of 2018) (-eval expr {} nil nil)))))

    (testing "Static year-month"
      (are [elm res] (= res (compile {} elm))
        #elm/date "2019-03"
        (YearMonth/of 2019 3)))

    (testing "Dynamic year-month"
      (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
        #elm/date [#elm/integer "2019" #elm/add [#elm/integer "2" #elm/integer "1"]]
        (YearMonth/of 2019 3)))

    (testing "Dynamic Null month and day"
      (let [elm #elm/date [#elm/integer "2020"
                           #elm/singleton-from patient-retrieve-elm
                           #elm/singleton-from patient-retrieve-elm]
            expr (compile context elm)]
        (is (= (Year/of 2020) (-eval expr {} nil nil)))))

    (testing "Dynamic Null day"
      (let [elm #elm/date [#elm/integer "2018"
                           #elm/integer "5"
                           #elm/singleton-from patient-retrieve-elm]
            expr (compile context elm)]
        (is (= (YearMonth/of 2018 5) (-eval expr {} nil nil)))))

    (testing "Static date"
      (are [elm res] (= res (compile {} elm))
        #elm/date "2019-03-23"
        (LocalDate/of 2019 3 23)))

    (testing "Dynamic date"
      (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
        #elm/date [#elm/integer "2019" #elm/integer "3"
                   #elm/add [#elm/integer "22" #elm/integer "1"]]
        (LocalDate/of 2019 3 23)))

    (testing "an ELM year (only literals) always compiles to a Year"
      (satisfies-prop 100
                      (prop/for-all [year (s/gen :elm/literal-year)]
                        (instance? Year (compile {} year)))))

    (testing "an ELM year-month (only literals) always compiles to a YearMonth"
      (satisfies-prop 100
                      (prop/for-all [year-month (s/gen :elm/literal-year-month)]
                        (instance? YearMonth (compile {} year-month)))))

    (testing "an ELM date (only literals) always compiles to something implementing Temporal"
      (satisfies-prop 100
                      (prop/for-all [date (s/gen :elm/literal-date)]
                        (instance? Temporal (compile {} date)))))))


;; 18.7. DateFrom
;;
;; The DateFrom operator returns the date (with no time components specified) of
;; the argument.
;;
;; If the argument is null, the result is null.
(deftest compile-date-from-test
  (are [x res] (= res (-eval (compile {} #elm/date-from x) {:now now} nil nil))
    #elm/date "2019-04-17" (LocalDate/of 2019 4 17)
    #elm/date-time "2019-04-17T12:48" (LocalDate/of 2019 4 17))

  (testing-unary-null elm/date-from))


;; 18.8. DateTime
;;
;; The DateTime operator constructs a DateTime value from the given components.
;;
;; At least one component other than timezoneOffset must be specified, and no
;; component may be specified at a precision below an unspecified precision. For
;; example, hour may be null, but if it is, minute, second, and millisecond must
;; all be null as well.
;;
;; If timezoneOffset is not specified, it is defaulted to the timezone offset of
;; the evaluation request.
(deftest compile-date-time-test
  (let [context {:eval-context "Patient" :node (mem-node-with [])}]
    (testing "Static Null year"
      (is (nil? (compile {} #elm/date-time [{:type "null"}]))))

    (testing "Static year"
      (is (= (Year/of 2019) (compile {} #elm/date-time "2019"))))

    (testing "Dynamic Null year"
      (let [elm #elm/date-time [#elm/singleton-from patient-retrieve-elm]
            expr (compile context elm)]
        (is (nil? (-eval expr {} nil nil)))))

    (testing "Dynamic year"
      (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
        #elm/date-time [#elm/add [#elm/integer "2018" #elm/integer "1"]]
        (Year/of 2019)))

    (testing "Dynamic Null month"
      (let [elm #elm/date-time [#elm/integer "2018"
                                #elm/singleton-from patient-retrieve-elm]
            expr (compile context elm)]
        (is (= (Year/of 2018) (-eval expr {} nil nil)))))

    (testing "Static year-month"
      (are [elm res] (= res (compile {} elm))
        #elm/date-time "2019-03"
        (YearMonth/of 2019 3)))

    (testing "Dynamic year-month"
      (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
        #elm/date-time [#elm/integer "2019" #elm/add [#elm/integer "2" #elm/integer "1"]]
        (YearMonth/of 2019 3)))

    (testing "Dynamic Null month and day"
      (let [elm #elm/date-time [#elm/integer "2020"
                                #elm/singleton-from patient-retrieve-elm
                                #elm/singleton-from patient-retrieve-elm]
            expr (compile context elm)]
        (is (= (Year/of 2020) (-eval expr {} nil nil)))))

    (testing "Dynamic Null day"
      (let [elm #elm/date-time [#elm/integer "2018"
                                #elm/integer "5"
                                #elm/singleton-from patient-retrieve-elm]
            expr (compile context elm)]
        (is (= (YearMonth/of 2018 5) (-eval expr {} nil nil)))))

    (testing "Static date"
      (are [elm res] (= res (compile {} elm))
        #elm/date-time "2019-03-23"
        (LocalDate/of 2019 3 23)))

    (testing "Dynamic date"
      (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
        #elm/date-time [#elm/integer "2019" #elm/integer "3"
                        #elm/add [#elm/integer "22" #elm/integer "1"]]
        (LocalDate/of 2019 3 23)))

    (testing "Static hour"
      (are [elm res] (= res (compile {} elm))
        #elm/date-time "2019-03-23T12"
        (LocalDateTime/of 2019 3 23 12 0 0)))

    (testing "Dynamic hour"
      (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
        #elm/date-time [#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                        #elm/add [#elm/integer "11" #elm/integer "1"]]
        (LocalDateTime/of 2019 3 23 12 0 0)))

    (testing "minute"
      (are [elm res] (= res (compile {} elm))
        #elm/date-time "2019-03-23T12:13"
        (LocalDateTime/of 2019 3 23 12 13 0)))

    (testing "second"
      (are [elm res] (= res (compile {} elm))
        #elm/date-time "2019-03-23T12:13:14"
        (LocalDateTime/of 2019 3 23 12 13 14)))

    (testing "millisecond"
      (are [elm res] (= res (compile {} elm))
        #elm/date-time "2019-03-23T12:13:14.1"
        (LocalDateTime/of 2019 3 23 12 13 14 1000000)))

    (testing "Invalid DateTime above max value"
      (are [elm] (thrown? Exception (compile {} elm))
        #elm/date-time "10000-12-31T23:59:59.999"))

    (testing "with offset"
      (are [elm res] (= res (-eval (compile {} elm) {:now now} nil nil))
        #elm/date-time [#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                        #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                        #elm/decimal "-2"]
        (LocalDateTime/of 2019 3 23 14 13 14)

        #elm/date-time [#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                        #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                        #elm/decimal "-1"]
        (LocalDateTime/of 2019 3 23 13 13 14)

        #elm/date-time [#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                        #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                        #elm/decimal "0"]
        (LocalDateTime/of 2019 3 23 12 13 14)

        #elm/date-time [#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                        #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                        #elm/decimal "1"]
        (LocalDateTime/of 2019 3 23 11 13 14)

        #elm/date-time [#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                        #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                        #elm/decimal "2"]
        (LocalDateTime/of 2019 3 23 10 13 14)

        #elm/date-time [#elm/integer "2012" #elm/integer "3" #elm/integer "10"
                        #elm/integer "10" #elm/integer "20" #elm/integer "0" #elm/integer "999"
                        #elm/decimal "7"]
        (LocalDateTime/of 2012 3 10 3 20 0 999000000)))

    (testing "with decimal offset"
      (are [elm res] (= res (-eval (compile {} elm) {:now now} nil nil))
        #elm/date-time [#elm/integer "2019" #elm/integer "3" #elm/integer "23"
                        #elm/integer "12" #elm/integer "13" #elm/integer "14" #elm/integer "0"
                        #elm/decimal "1.5"]
        (LocalDateTime/of 2019 3 23 10 43 14)))

    (testing "an ELM date-time (only literals) always evaluates to something implementing Temporal"
      (satisfies-prop 100
                      (prop/for-all [date-time (s/gen :elm/literal-date-time)]
                        (instance? Temporal (-eval (compile {} date-time) {:now now} nil nil)))))))


;; 18.9. DateTimeComponentFrom
;;
;; The DateTimeComponentFrom operator returns the specified component of the
;; argument.
;;
;; If the argument is null, the result is null.
;
;; The precision must be one of Year, Month, Day, Hour, Minute, Second, or
;; Millisecond. Note specifically that since there is variability how weeks are
;; counted, Week precision is not supported, and will result in an error.
(deftest compile-date-time-component-from-test
  (are [x precision res] (= res (-eval (compile {} {:type "DateTimeComponentFrom" :operand x :precision precision}) {:now now} nil nil))
    #elm/date "2019-04-17" "Year" 2019
    #elm/date-time "2019-04-17T12:48" "Hour" 12
    {:type "Null"} "Year" nil))


;; 18.11. DurationBetween
(deftest compile-duration-between-test
  (testing "Year precision"
    (are [a b res] (= res (-eval (compile {} (elm/duration-between [a b "Year"])) {} nil nil))
      #elm/date "2018" #elm/date "2019" 1
      #elm/date "2018" #elm/date "2017" -1
      #elm/date "2018" #elm/date "2018" 0))

  (testing "Month precision"
    (are [a b res] (= res (-eval (compile {} (elm/duration-between [a b "Month"])) {} nil nil))
      #elm/date "2018-01" #elm/date "2018-02" 1
      #elm/date "2018-01" #elm/date "2017-12" -1
      #elm/date "2018-01" #elm/date "2018-01" 0))

  (testing "Day precision"
    (are [a b res] (= res (-eval (compile {} (elm/duration-between [a b "Day"])) {:now now} nil nil))
      #elm/date "2018-01-01" #elm/date "2018-01-02" 1
      #elm/date "2018-01-01" #elm/date "2017-12-31" -1
      #elm/date "2018-01-01" #elm/date "2018-01-01" 0))

  (testing "Hour precision"
    (are [a b res] (= res (-eval (compile {} (elm/duration-between [a b "Hour"])) {:now now} nil nil))
      #elm/date-time "2018-01-01T00" #elm/date-time "2018-01-01T01" 1
      #elm/date-time "2018-01-01T00" #elm/date-time "2017-12-31T23" -1
      #elm/date-time "2018-01-01T00" #elm/date-time "2018-01-01T00" 0))

  (testing "Calculating the duration between temporals with insufficient precision results in null."
    (are [a b p] (nil? (-eval (compile {} (elm/duration-between [a b p])) {} nil nil))
      #elm/date "2018" #elm/date "2018" "Month"
      #elm/date "2018-01" #elm/date "2018-01" "Day"
      #elm/date "2018-01-01" #elm/date "2018-01-01" "Hour")))


;; 18.12. Not Equal
;;
;; See 12.7. NotEqual


;; 18.14. SameAs
;;
;; The SameAs operator is defined for Date, DateTime, and Time values, as well
;; as intervals.
;;
;; For the Interval overloads, the SameAs operator returns true if the intervals
;; start and end at the same value, using the semantics described in the Start
;; and End operator to determine interval boundaries.
;;
;; The SameAs operator compares two Date, DateTime, or Time values to the
;; specified precision for equality. Individual component values are compared
;; starting from the year component down to the specified precision. If all
;; values are specified and have the same value for each component, then the
;; result is true. If a compared component is specified in both dates, but the
;; values are not the same, then the result is false. Otherwise the result is
;; null, as there is not enough information to make a determination.
;;
;; If no precision is specified, the comparison is performed beginning with
;; years (or hours for time values) and proceeding to the finest precision
;; specified in either input.
;;
;; For Date values, precision must be one of year, month, or day.
;;
;; For DateTime values, precision must be one of year, month, day, hour, minute,
;; second, or millisecond.
;;
;; For Time values, precision must be one of hour, minute, second, or
;; millisecond.
;;
;; Note specifically that due to variability in the way week numbers are
;; determined, comparisons involving weeks are not supported.
;;
;; As with all date and time calculations, comparisons are performed respecting
;; the timezone offset.
;;
;; If either argument is null, the result is null.
(deftest compile-same-as-test
  (are [x y res] (= res (-eval (compile {} {:type "SameAs" :operand [x y]}) {} nil nil))
    #elm/date "2019-04-17" #elm/date "2019-04-17" true
    #elm/date "2019-04-17" #elm/date "2019-04-18" false)

  (testing "With year precision"
    (are [x y res] (= res (-eval (compile {} {:type "SameAs" :operand [x y] :precision "year"}) {} nil nil))
      #elm/date "2019-04-17" #elm/date "2019-04-17" true
      #elm/date "2019-04-17" #elm/date "2019-04-18" true)))


;; 18.13. Now
;;
;; The Now operator returns the date and time of the start timestamp associated
;; with the evaluation request. Now is defined in this way for two reasons:
;;
;; 1) The operation will always return the same value within any given
;; evaluation, ensuring that the result of an expression containing Now will
;; always return the same result.
;;
;; 2) The operation will return the timestamp associated with the evaluation
;; request, allowing the evaluation to be performed with the same timezone
;; offset information as the data delivered with the evaluation request.
(deftest compile-now-test
  (are [elm res] (= res (-eval (compile {} elm) {:now now} nil nil))
    {:type "Now"}
    now))


;; 18.15. SameOrBefore
;;
;; The SameOrBefore operator is defined for Date, DateTime, and Time values, as
;; well as intervals.
;;
;; For the Interval overload, the SameOrBefore operator returns true if the
;; first interval ends on or before the second one starts. In other words, if
;; the ending point of the first interval is less than or equal to the starting
;; point of the second interval, using the semantics described in the Start and
;; End operators to determine interval boundaries.
(deftest compile-same-or-before-test
  (testing "Interval"
    (are [x y res] (= res (-eval (compile {} {:type "SameOrBefore" :operand [x y]}) {} nil nil))
      #elm/interval [#elm/integer "1" #elm/integer "2"]
      #elm/interval [#elm/integer "2" #elm/integer "3"] true)))


;; 18.15. SameOrAfter
;;
;; The SameOrAfter operator is defined for Date, DateTime, and Time values, as
;; well as intervals.
;;
;; For the Interval overload, the SameOrAfter operator returns true if the first
;; interval starts on or after the second one ends. In other words, if the
;; starting point of the first interval is greater than or equal to the ending
;; point of the second interval, using the semantics described in the Start and
;; End operators to determine interval boundaries.
(deftest compile-same-or-after-test
  (testing "Interval"
    (are [x y res] (= res (-eval (compile {} {:type "SameOrAfter" :operand [x y]}) {} nil nil))
      #elm/interval [#elm/integer "2" #elm/integer "3"]
      #elm/interval [#elm/integer "1" #elm/integer "2"] true)))


;; 18.18. Time
;;
;; The Time operator constructs a time value from the given components.
;;
;; At least one component other than timezoneOffset must be specified, and no
;; component may be specified at a precision below an unspecified precision.
;; For example, minute may be null, but if it is, second, and millisecond
;; must all be null as well.
;;
;; If timezoneOffset is not specified, it is defaulted to the timezone offset
;; of the evaluation request.
(deftest compile-time-test
  (testing "Static hour"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/integer "12"]
      (local-time 12)))

  (testing "Dynamic hour"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/time [#elm/add [#elm/integer "11" #elm/integer "1"]]
      (local-time 12)))

  (testing "Static hour-minute"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/integer "12" #elm/integer "13"]
      (local-time 12 13)))

  (testing "Dynamic hour-minute"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/time [#elm/integer "12" #elm/add [#elm/integer "12"
                                         #elm/integer "1"]]
      (local-time 12 13)))

  (testing "Static hour-minute-second"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/integer "12" #elm/integer "13" #elm/integer "14"]
      (local-time 12 13 14)))

  (testing "Dynamic hour-minute-second"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/time [#elm/integer "12" #elm/integer "13"
                 #elm/add [#elm/integer "13" #elm/integer "1"]]
      (local-time 12 13 14)))

  (testing "Static hour-minute-second-millisecond"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/integer "12" #elm/integer "13" #elm/integer "14"
                 #elm/integer "15"]
      (local-time 12 13 14 15)))

  (testing "Dynamic hour-minute-second-millisecond"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/time [#elm/integer "12" #elm/integer "13" #elm/integer "14"
                 #elm/add [#elm/integer "14" #elm/integer "1"]]
      (local-time 12 13 14 15)))

  (testing "an ELM time (only literals) always compiles to a LocalTime"
    (satisfies-prop 100
                    (prop/for-all [time (s/gen :elm/time)]
                      (local-time? (compile {} time)))))

  )
(comment (s/exercise :elm/time))


;; 18.21. TimeOfDay
;;
;; The TimeOfDay operator returns the time-of-day of the start timestamp
;; associated with the evaluation request. See the Now operator for more
;; information on the rationale for defining the TimeOfDay operator in this way.
(deftest compile-time-of-day-test
  (are [res] (= res (-eval (compile {} {:type "TimeOfDay"}) {:now now} nil nil))
    (.toLocalTime now)))


;; 18.22. Today
;;
;; The Today operator returns the date (with no time component) of the start
;; timestamp associated with the evaluation request. See the Now operator for
;; more information on the rationale for defining the Today operator in this
;; way.
(deftest compile-today-test
  (are [res] (= res (-eval (compile {} {:type "Today"}) {:now now} nil nil))
    (.toLocalDate now)))



;; 19. Interval Operators

(def interval-zero #elm/interval [#elm/integer "0" #elm/integer "0"])

;; 19.1. Interval
;;
;; The Interval selector defines an interval value. An interval must be defined
;; using a point type that supports comparison, as well as Successor and
;; Predecessor operations, and Minimum and Maximum Value operations.
;;
;; The low and high bounds of the interval may each be defined as open or
;; closed. Following standard terminology usage in interval mathematics, an open
;; interval is defined to exclude the specified point, whereas a closed interval
;; includes the point. The default is closed, indicating an inclusive interval.
;;
;; The low and high elements are both optional. If the low element is not
;; specified, the low bound of the resulting interval is null. If the high
;; element is not specified, the high bound of the resulting interval is null.
;;
;; The static type of the low bound determines the type of the interval, and the
;; high bound must be of the same type.
;;
;; If the low bound of the interval is null and open, the low bound of the
;; interval is interpreted as unknown, and computations involving the low
;; boundary will result in null.
;;
;; If the low bound of the interval is null and closed, the interval is
;; interpreted to start at the minimum value of the point type, and computations
;; involving the low boundary will be performed with that value.
;;
;; If the high bound of the interval is null and open, the high bound of the
;; interval is unknown, and computations involving the high boundary will result
;; in null.
;;
;; If the high bound of the interval is null and closed, the interval is
;; interpreted to end at the maximum value of the point type, and computations
;; involving the high boundary will be performed with that interpretation.
(deftest compile-interval-test
  (testing "Static"
    (are [elm res] (= res (compile {} elm))
      #elm/interval [#elm/integer "1" #elm/integer "2"] (interval 1 2)
      #elm/interval [#elm/decimal "1" #elm/decimal "2"] (interval 1M 2M)

      #elm/interval [:< #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
                     #elm/integer "1"]
      (interval nil 1)

      #elm/interval [#elm/integer "1"
                     #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}] :>]
      (interval 1 nil)

      #elm/interval [:< #elm/integer "1" #elm/integer "2"] (interval 2 2)
      #elm/interval [#elm/integer "1" #elm/integer "2" :>] (interval 1 1)
      #elm/interval [:< #elm/integer "1" #elm/integer "3" :>] (interval 2 2)))

  (let [context {:eval-context "Patient" :node (mem-node-with [])}]
    (testing "Dynamic"
      (are [elm res] (= res (-eval (compile context elm) {} nil nil))
        (elm/interval [:< (elm/as ["{urn:hl7-org:elm-types:r1}Integer" (elm/singleton-from patient-retrieve-elm)])
                       #elm/integer "1"])
        (interval nil 1)

        (elm/interval [#elm/integer "1"
                       (elm/as ["{urn:hl7-org:elm-types:r1}Integer" (elm/singleton-from patient-retrieve-elm)]) :>])
        (interval 1 nil))))

  (testing "Invalid interval"
    (are [elm] (thrown? Exception (-eval (compile {} elm) {} nil nil))
      #elm/interval [#elm/integer "5" #elm/integer "3"])))


;; 19.2. After
;;
;; The After operator is defined for Intervals, as well as Date, DateTime, and
;; Time values.
;;
;; For the Interval overload, the After operator returns true if the first
;; interval starts after the second one ends. In other words, if the starting
;; point of the first interval is greater than the ending point of the second
;; interval using the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; For the Date, DateTime, and Time overloads, the After operator returns true
;; if the first datetime is after the second datetime at the specified level of
;; precision. The comparison is performed by considering each precision in
;; order, beginning with years (or hours for time values). If the values are the
;; same, comparison proceeds to the next precision; if the first value is
;; greater than the second, the result is true; if the first value is less than
;; the second, the result is false; if either input has no value for the
;; precision, the comparison stops and the result is null; if the specified
;; precision has been reached, the comparison stops and the result is false.
;;
;; If no precision is specified, the comparison is performed beginning with
;; years (or hours for time values) and proceeding to the finest precision
;; specified in either input.
;;
;; For Date values, precision must be one of year, month, or day.
;;
;; For DateTime values, precision must be one of year, month, day, hour, minute,
;; second, or millisecond.
;;
;; For Time values, precision must be one of hour, minute, second, or
;; millisecond.
;;
;; Note specifically that due to variability in the way week numbers are
;; determined, comparisons involving weeks are not supported.
;;
;; As with all date and time calculations, comparisons are performed respecting
;; the timezone offset.
;;
;; If either argument is null, the result is null.
(deftest compile-after-test
  (testing "Interval"
    (testing "null arguments result in null"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        interval-zero {:type "Null"} nil
        {:type "Null"} interval-zero nil))

    (testing "if both intervals are closed, the start of the first (3) has to be greater then the end of the second (2)"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        #elm/interval [#elm/integer "3" #elm/integer "4"]
        #elm/interval [#elm/integer "1" #elm/integer "2"] true
        #elm/interval [#elm/integer "2" #elm/integer "3"]
        #elm/interval [#elm/integer "1" #elm/integer "2"] false))

    (testing "if one of the intervals is open, start and end can be the same (2)"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        #elm/interval [#elm/integer "2" #elm/integer "3"]
        #elm/interval [#elm/integer "1" #elm/integer "2" :>] true
        #elm/interval [:< #elm/integer "2" #elm/integer "3"]
        #elm/interval [#elm/integer "1" #elm/integer "2"] true
        #elm/interval [:< #elm/integer "2" #elm/integer "3"]
        #elm/interval [#elm/integer "1" #elm/integer "2" :>] true))

    (testing "if both intervals are open, start and end can overlap slightly"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        #elm/interval [:< #elm/integer "2" #elm/integer "4"]
        #elm/interval [#elm/integer "1" #elm/integer "3" :>] true))

    (testing "if one of the relevant bounds is infinity, the result is false"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/integer "3"]
        #elm/interval [#elm/integer "1" #elm/integer "2"] false
        #elm/interval [#elm/integer "2" #elm/integer "3"]
        #elm/interval [#elm/integer "1" {:type "Null"}] false))

    (testing "if the second interval has an unknown high bound, the result is null"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        #elm/interval [#elm/integer "2" #elm/integer "3"]
        #elm/interval [#elm/integer "1" {:type "Null"} :>] nil))))


;; 19.3. Before
;;
;; The Before operator is defined for Intervals, as well as Date, DateTime, and
;; Time values.
;;
;; For the Interval overload, the Before operator returns true if the first
;; interval ends before the second one starts. In other words, if the ending
;; point of the first interval is less than the starting point of the second
;; interval, using the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; For the Date, DateTime, and Time overloads, the comparison is performed by
;; considering each precision in order, beginning with years (or hours for time
;; values). If the values are the same, comparison proceeds to the next
;; precision; if the first value is less than the second, the result is true; if
;; the first value is greater than the second, the result is false; if either
;; input has no value for the precision, the comparison stops and the result is
;; null; if the specified precision has been reached, the comparison stops and
;; the result is false.
;;
;; If no precision is specified, the comparison is performed beginning with
;; years (or hours for time values) and proceeding to the finest precision
;; specified in either input.
;;
;; For Date values, precision must be one of year, month, or day.
;;
;; For DateTime values, precision must be one of year, month, day, hour, minute,
;; second, or millisecond.
;;
;; For Time values, precision must be one of hour, minute, second, or
;; millisecond.
;;
;; Note specifically that due to variability in the way week numbers are
;; determined, comparisons involving weeks are not supported.
;;
;; As with all date and time calculations, comparisons are performed respecting
;; the timezone offset.
;;
;; If either argument is null, the result is null.
(deftest compile-before-test
  (testing "Interval"
    (testing "null arguments result in null"
      (are [x y res] (= res (-eval (compile {} (elm/before [x y])) {} nil nil))
        interval-zero {:type "Null"} nil
        {:type "Null"} interval-zero nil))

    (testing "if both intervals are closed, the end of the first (2) has to be less then the start of the second (3)"
      (are [a b res] (= res (-eval (compile {} (elm/before [a b])) {} nil nil))
        #elm/interval [#elm/integer "1" #elm/integer "2"]
        #elm/interval [#elm/integer "3" #elm/integer "4"] true
        #elm/interval [#elm/integer "1" #elm/integer "2"]
        #elm/interval [#elm/integer "2" #elm/integer "3"] false))

    (testing "if one of the intervals is open, start and end can be the same (2)"
      (are [a b res] (= res (-eval (compile {} (elm/before [a b])) {} nil nil))
        #elm/interval [#elm/integer "1" #elm/integer "2" :>]
        #elm/interval [#elm/integer "2" #elm/integer "3"] true
        #elm/interval [#elm/integer "1" #elm/integer "2"]
        #elm/interval [:< #elm/integer "2" #elm/integer "3"] true
        #elm/interval [#elm/integer "1" #elm/integer "2" :>]
        #elm/interval [:< #elm/integer "2" #elm/integer "3"] true))

    (testing "if both intervals are open, start and end can overlap slightly"
      (are [a b res] (= res (-eval (compile {} (elm/before [a b])) {} nil nil))
        #elm/interval [#elm/integer "1" #elm/integer "3" :>]
        #elm/interval [:< #elm/integer "2" #elm/integer "4"] true))

    (testing "if one of the relevant bounds is infinity, the result is false"
      (are [a b res] (= res (-eval (compile {} (elm/before [a b])) {} nil nil))
        #elm/interval [#elm/integer "1" {:type "Null"}]
        #elm/interval [#elm/integer "2" #elm/integer "3"] false
        #elm/interval [#elm/integer "1" #elm/integer "2"]
        #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/integer "3"] false))

    (testing "if the second interval has an unknown low bound, the result is null"
      (are [a b res] (= res (-eval (compile {} (elm/before [a b])) {} nil nil))
        #elm/interval [#elm/integer "1" #elm/integer "2"]
        #elm/interval [:< {:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/integer "3"] nil))))


;; 19.4. Collapse
;;
;; The Collapse operator returns the unique set of intervals that completely
;; covers the ranges present in the given list of intervals.
;;
;; The operation is performed by combining successive intervals in the input
;; that either overlap or meet, using the semantics defined for the Overlaps and
;; Meets operators. Note that because those operators are themselves defined in
;; terms of interval successor and predecessor operators, sets of Date-,
;; DateTime-, and Time-based intervals that are only defined to a particular
;; precision will calculate meets and overlaps at that precision. For example, a
;; list of DateTime-based intervals where the boundaries are all specified to
;; the hour will collapse at the hour precision, unless the collapse precision
;; is overridden with the per argument.
;;
;; The per argument determines the precision at which the collapse is computed
;; and must be a quantity-valued expression compatible with the interval point
;; type. For numeric intervals, this means a default unit ('1'), for Date-,
;; DateTime-, and Time-valued intervals, this means a temporal duration.
;;
;; If the per argument is null, the default unit interval for the point type of
;; the intervals involved will be used (i.e. an interval with the same starting
;; and ending boundary).
;;
;; If the list of intervals is empty, the result is empty. If the list of
;; intervals contains a single interval, the result is a list with that
;; interval. If the list of intervals contains nulls, they will be excluded from
;; the resulting list.
;;
;; If the source argument is null, the result is null.
(deftest compile-collapse-test
  (are [source per res] (= res (-eval (compile {} (elm/collapse [source per])) {} nil nil))
    #elm/list [#elm/interval [#elm/integer "1" #elm/integer "2"]]
    {:type "Null"}
    [(interval 1 2)]

    #elm/list [#elm/interval [#elm/integer "1" #elm/integer "2"]
               #elm/interval [#elm/integer "2" #elm/integer "3"]]
    {:type "Null"}
    [(interval 1 3)]

    #elm/list [{:type "Null"}] {:type "Null"} []
    #elm/list [{:type "Null"} {:type "Null"}] {:type "Null"} []
    #elm/list [] {:type "Null"} []

    {:type "Null"} {:type "Null"} nil))


;; 19.5. Contains
;;
;; The Contains operator returns true if the first operand contains the second.
;;
;; There are two overloads of this operator: 1. List, T : The type of T must be
;; the same as the element type of the list. 2. Interval, T : The type of T must
;; be the same as the point type of the interval.
;;
;; For the List, T overload, this operator returns true if the given element is
;; in the list, using equality semantics.
;;
;; For the Interval, T overload, this operator returns true if the given point
;; is greater than or equal to the starting point of the interval, and less than
;; or equal to the ending point of the interval. For open interval boundaries,
;; exclusive comparison operators are used. For closed interval boundaries, if
;; the interval boundary is null, the result of the boundary comparison is
;; considered true. If precision is specified and the point type is a Date,
;; DateTime, or Time type, comparisons used in the operation are performed at
;; the specified precision.
;;
;; If either argument is null, the result is null.
(deftest compile-contains-test
  (testing "Interval"
    (testing "Null"
      (are [interval x res] (= res (-eval (compile {} (elm/contains [interval x])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [interval x res] (= res (-eval (compile {} (elm/contains [interval x])) {} nil nil))
        #elm/interval [#elm/integer "1" #elm/integer "1"] #elm/integer "1" true
        #elm/interval [#elm/integer "1" #elm/integer "1"] #elm/integer "2" false)))

  (testing "List"
    (are [list x res] (= res (-eval (compile {} (elm/contains [list x])) {} nil nil))
      #elm/list [] #elm/integer "1" false

      #elm/list [#elm/integer "1"] #elm/integer "1" true
      #elm/list [#elm/integer "1"] #elm/integer "2" false

      #elm/list [#elm/quantity [1 "m"]] #elm/quantity [100 "cm"] true

      #elm/list [#elm/date "2019"] #elm/date "2019-01" false

      #elm/list [] {:type "Null"} nil)))


;; 19.6. End
;;
;; The End operator returns the ending point of an interval.
;;
;; If the high boundary of the interval is open, this operator returns the
;; Predecessor of the high value of the interval. Note that if the high value of
;; the interval is null, the result is null.
;;
;; If the high boundary of the interval is closed and the high value of the
;; interval is not null, this operator returns the high value of the interval.
;; Otherwise, the result is the maximum value of the point type of the interval.
;;
;; If the argument is null, the result is null.
(deftest compile-end-test
  (testing "Null"
    (are [x res] (= res (-eval (compile {} {:type "End" :operand x}) {} nil nil))
      {:type "Null"} nil))

  (testing "Integer"
    (are [x res] (= res (-eval (compile {} {:type "End" :operand x}) {} nil nil))
      #elm/interval [#elm/integer "1" #elm/integer "2"] 2
      #elm/interval [#elm/integer "1" #elm/integer "2" :>] 1
      #elm/interval [#elm/integer "1" {:type "Null"}] Integer/MAX_VALUE))

  (testing "Decimal"
    (are [x res] (= res (-eval (compile {} {:type "End" :operand x}) {} nil nil))
      #elm/interval [#elm/decimal "1" #elm/decimal "2.1"] 2.1M
      #elm/interval [#elm/decimal "1" #elm/decimal "2.1" :>] 2.09999999M
      #elm/interval [#elm/decimal "1" {:type "Null"}] decimal/max)))


;; 19.7. Ends
;;
;; The Ends operator returns true if the first interval ends the second. In
;; other words, if the starting point of the first interval is greater than or
;; equal to the starting point of the second, and the ending point of the first
;; interval is equal to the ending point of the second.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If precision is specified and the point type is a Date, DateTime, or Time
;; type, comparisons used in the operation are performed at the specified
;; precision.
;;
;; If either argument is null, the result is null.
(deftest compile-ends-test
  (testing "Null"
    (are [a b res] (= res (-eval (compile {} {:type "Ends" :operand [a b]}) {} nil nil))
      {:type "Null"} interval-zero nil
      interval-zero {:type "Null"} nil))

  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} {:type "Ends" :operand [a b]}) {} nil nil))
      #elm/interval [#elm/integer "1" #elm/integer "3"]
      #elm/interval [#elm/integer "1" #elm/integer "3"] true
      #elm/interval [#elm/integer "2" #elm/integer "3"]
      #elm/interval [#elm/integer "1" #elm/integer "3"] true
      #elm/interval [#elm/integer "1" #elm/integer "3"]
      #elm/interval [#elm/integer "2" #elm/integer "3"] false)))


;; 19.10. Except
;;
;; The Except operator returns the set difference of the two arguments.
;;
;; This operator has two overloads: 1. List, List 2. Interval, Interval
;;
;; For the list overload, this operator returns a list with the elements that
;; appear in the first operand, that do not appear in the second operand, using
;; equality semantics. The operator is defined with set semantics, meaning that
;; each element will appear in the result at most once, and that there is no
;; expectation that the order of the inputs will be preserved in the results.
;;
;; For the interval overload, this operator returns the portion of the first
;; interval that does not overlap with the second. If the second argument is
;; properly contained within the first and does not start or end it, this
;; operator returns null.
;;
;; If either argument is null, the result is null.
(deftest compile-except-test
  (testing "Null"
    (are [a b res] (= res (-eval (compile {} (elm/except [a b])) {} nil nil))
      {:type "Null"} {:type "Null"} nil))

  (testing "List"
    (are [a b res] (= res (-eval (compile {} (elm/except [a b])) {} nil nil))
      #elm/list [] #elm/list [] []
      #elm/list [] #elm/list [#elm/integer "1"] []
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "1"] []
      #elm/list [#elm/integer "1"] #elm/list [] [1]
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "2"] [1]
      #elm/list [#elm/integer "1" #elm/integer "2"] #elm/list [#elm/integer "2"] [1]
      #elm/list [#elm/integer "1" #elm/integer "2"] #elm/list [#elm/integer "1"] [2]

      #elm/list [] {:type "Null"} nil))

  (testing "Interval"
    (testing "Null"
      (are [a b res] (= res (-eval (compile {} (elm/except [a b])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [a b res] (= res (-eval (compile {} (elm/except [a b])) {} nil nil))
        #elm/interval [#elm/integer "1" #elm/integer "3"]
        #elm/interval [#elm/integer "3" #elm/integer "4"]
        (interval 1 2)

        #elm/interval [#elm/integer "3" #elm/integer "5"]
        #elm/interval [#elm/integer "1" #elm/integer "3"]
        (interval 4 5)))))


;; 19.12. In
;;
;; Normalized to Contains
(deftest compile-in-test
  (unsupported-binary-operand "In"))


;; 19.13. Includes
;;
;; The Includes operator returns true if the first operand completely includes
;; the second.
;;
;; There are two overloads of this operator: 1. List, List : The element type of
;; both lists must be the same. 2. Interval, Interval : The point type of both
;; intervals must be the same.
;;
;; For the List, List overload, this operator returns true if the first operand
;; includes every element of the second operand, using equality semantics.
;;
;; For the Interval, Interval overload, this operator returns true if starting
;; point of the first interval is less than or equal to the starting point of
;; the second interval, and the ending point of the first interval is greater
;; than or equal to the ending point of the second interval. If precision is
;; specified and the point type is a Date, DateTime, or Time type, comparisons
;; used in the operation are performed at the specified precision.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If either argument is null, the result is null.
(deftest compile-includes-test
  (testing "Null"
    (are [a b res] (= res (-eval (compile {} (elm/includes [a b])) {} nil nil))
      {:type "Null"} {:type "Null"} nil))

  (testing "List"
    (are [a b res] (= res (-eval (compile {} (elm/includes [a b])) {} nil nil))
      #elm/list [] #elm/list [] true
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "1"] true
      #elm/list [#elm/integer "1" #elm/integer "2"] #elm/list [#elm/integer "1"] true

      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] false

      #elm/list [] {:type "Null"} nil))

  (testing "Interval"
    (testing "Null"
      (are [a b res] (= res (-eval (compile {} (elm/includes [a b])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [a b res] (= res (-eval (compile {} (elm/includes [a b])) {} nil nil))
        #elm/interval [#elm/integer "1" #elm/integer "2"]
        #elm/interval [#elm/integer "1" #elm/integer "2"] true
        #elm/interval [#elm/integer "1" #elm/integer "2"]
        #elm/interval [#elm/integer "1" #elm/integer "3"] false))))


;; 19.14. IncludedIn
;;
;; Normalized to Includes
(deftest compile-included-in-test
  (unsupported-binary-operand "IncludedIn"))


;; 19.15. Intersect
;;
;; The Intersect operator returns the intersection of its arguments.
;;
;; This operator has two overloads: List Interval
;;
;; For the list overload, this operator returns a list with the elements that
;; appear in both lists, using equality semantics. The operator is defined with
;; set semantics, meaning that each element will appear in the result at most
;; once, and that there is no expectation that the order of the inputs will be
;; preserved in the results.
;;
;; For the interval overload, this operator returns the interval that defines
;; the overlapping portion of both arguments. If the arguments do not overlap,
;; this operator returns null.
;;
;; If either argument is null, the result is null.
;;
;; TODO: only implemented as binary operator because it's binary in CQL.
(deftest compile-intersect-test
  (testing "List"
    (are [a b res] (= res (compile {} #elm/intersect [a b]))
      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] []
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "1"] [1]
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "2"] []

      #elm/list [#elm/integer "1"]
      #elm/list [#elm/integer "1" #elm/integer "2"]
      [1]

      #elm/list [#elm/integer "1" #elm/integer "2"]
      #elm/list [#elm/integer "1"]
      [1])

    (testing-binary-null elm/intersect #elm/list []))

  (testing "Interval"
    (are [a b res] (= res (compile {} #elm/intersect [a b]))
      #elm/interval [#elm/integer "1" #elm/integer "2"]
      #elm/interval [#elm/integer "2" #elm/integer "3"]
      (interval 2 2)

      #elm/interval [#elm/integer "2" #elm/integer "3"]
      #elm/interval [#elm/integer "1" #elm/integer "2"]
      (interval 2 2)

      #elm/interval [#elm/integer "1" #elm/integer "10"]
      #elm/interval [#elm/integer "5" #elm/integer "8"]
      (interval 5 8)

      #elm/interval [#elm/integer "1" #elm/integer "10"]
      #elm/interval [#elm/integer "5" {:type "Null"} :>]
      nil

      #elm/interval [#elm/integer "1" #elm/integer "2"]
      #elm/interval [#elm/integer "3" #elm/integer "4"]
      nil)

    (testing-binary-null elm/intersect interval-zero)))


;; 19.16. Meets
;;
;; Normalized to MeetsBefore or MeetsAfter
(deftest compile-meets-test
  (unsupported-binary-operand "Meets"))


;; 19.17. MeetsBefore
;;
;; The MeetsBefore operator returns true if the first interval ends immediately
;; before the second interval starts. In other words, if the ending point of the
;; first interval is equal to the predecessor of the starting point of the
;; second.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If precision is specified and the point type is a Date, DateTime, or Time
;; type, comparisons used in the operation are performed at the specified
;; precision.
;;
;; If either argument is null, the result is null.
(deftest compile-meets-before-test
  (testing "Null"
    (are [x y res] (= res (-eval (compile {} (elm/meets-before [x y])) {} nil nil))
      interval-zero {:type "Null"} nil
      {:type "Null"} interval-zero nil))

  (testing "Integer"
    (are [x y res] (= res (-eval (compile {} (elm/meets-before [x y])) {} nil nil))
      #elm/interval [#elm/integer "1" #elm/integer "2"]
      #elm/interval [#elm/integer "3" #elm/integer "4"] true
      #elm/interval [#elm/integer "1" #elm/integer "2"]
      #elm/interval [#elm/integer "4" #elm/integer "5"] false)))


;; 19.18. MeetsAfter
;;
;; The MeetsAfter operator returns true if the first interval starts immediately
;; after the second interval ends. In other words, if the starting point of the
;; first interval is equal to the successor of the ending point of the second.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If precision is specified and the point type is a Date, DateTime, or Time
;; type, comparisons used in the operation are performed at the specified
;; precision.
;;
;; If either argument is null, the result is null.
(deftest compile-meets-after-test
  (testing "Null"
    (are [x y res] (= res (-eval (compile {} (elm/meets-after [x y])) {} nil nil))
      interval-zero {:type "Null"} nil
      {:type "Null"} interval-zero nil))

  (testing "Integer"
    (are [x y res] (= res (-eval (compile {} (elm/meets-after [x y])) {} nil nil))
      #elm/interval [#elm/integer "3" #elm/integer "4"]
      #elm/interval [#elm/integer "1" #elm/integer "2"] true
      #elm/interval [#elm/integer "4" #elm/integer "5"]
      #elm/interval [#elm/integer "1" #elm/integer "2"] false)))


;; 19.20. Overlaps
;;
;; Normalized to OverlapsBefore or OverlapsAfter
(deftest compile-overlaps-test
  (unsupported-binary-operand "Overlaps"))


;; 19.21. OverlapsBefore
;;
;; Normalized to ProperContains Start
(deftest compile-overlaps-before-test
  (unsupported-binary-operand "OverlapsBefore"))


;; 19.22. OverlapsAfter
;;
;; Normalized to ProperContains End
(deftest compile-overlaps-after-test
  (unsupported-binary-operand "OverlapsAfter"))


;; 19.23. PointFrom
;;
;; The PointFrom expression extracts the single point from the source interval.
;; The source interval must be a unit interval (meaning an interval with the
;; same starting and ending boundary), otherwise, a run-time error is thrown.
;;
;; If the source interval is null, the result is null.
(deftest compile-point-from-test
  (are [x res] (= res (-eval (compile {} {:type "PointFrom" :operand x}) {} nil nil))
    #elm/interval [#elm/integer "1" #elm/integer "1"] 1
    {:type "Null"} nil))


;; 19.24. ProperContains
;;
;; The ProperContains operator returns true if the first operand properly
;; contains the second.
;;
;; There are two overloads of this operator: List, T: The type of T must be the
;; same as the element type of the list. Interval, T : The type of T must be the
;; same as the point type of the interval.
;;
;; For the List, T overload, this operator returns true if the given element is
;; in the list, and it is not the only element in the list, using equality
;; semantics.
;;
;; For the Interval, T overload, this operator returns true if the given point
;; is greater than the starting point of the interval, and less than the ending
;; point of the interval, as determined by the Start and End operators. If
;; precision is specified and the point type is a Date, DateTime, or Time type,
;; comparisons used in the operation are performed at the specified precision.
;;
;; If either argument is null, the result is null.
(deftest compile-proper-contains-test
  (testing "Interval"
    (testing "Null"
      (are [interval x res] (= res (-eval (compile {} (elm/proper-contains [interval x])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [interval x res] (= res (-eval (compile {} (elm/proper-contains [interval x])) {} nil nil))
        #elm/interval [#elm/integer "1" #elm/integer "3"] #elm/integer "2" true
        #elm/interval [#elm/integer "1" #elm/integer "1"] #elm/integer "1" false
        #elm/interval [#elm/integer "1" #elm/integer "1"] #elm/integer "2" false))))


;; 19.25. ProperIn
;;
;; Normalized to ProperContains
(deftest compile-proper-in-test
  (unsupported-binary-operand "ProperIn"))


;; 19.26. ProperIncludes
;;
;; The ProperIncludes operator returns true if the first operand includes the
;; second, and is strictly larger.
;;
;; There are two overloads of this operator: List, List : The element type of
;; both lists must be the same. Interval, Interval : The point type of both
;; intervals must be the same.
;;
;; For the List, List overload, this operator returns true if the first list
;; includes every element of the second list, using equality semantics, and the
;; first list is strictly larger.
;;
;; For the Interval, Interval overload, this operator returns true if the first
;; interval includes the second interval, and the intervals are not equal. If
;; precision is specified and the point type is a Date, DateTime, or Time type,
;; comparisons used in the operation are performed at the specified precision.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If either argument is null, the result is null.
(deftest compile-proper-includes-test
  (testing "Null"
    (are [x y res] (= res (-eval (compile {} (elm/proper-includes [x y])) {} nil nil))
      {:type "Null"} {:type "Null"} nil))

  (testing "Interval"
    (testing "Null"
      (are [x y res] (= res (-eval (compile {} (elm/proper-includes [x y])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [x y res] (= res (-eval (compile {} (elm/proper-includes [x y])) {} nil nil))
        #elm/interval [#elm/integer "1" #elm/integer "3"]
        #elm/interval [#elm/integer "1" #elm/integer "2"] true
        #elm/interval [#elm/integer "1" #elm/integer "2"]
        #elm/interval [#elm/integer "1" #elm/integer "2"] false))))


;; 19.27. ProperIncludedIn
;;
;; Normalized to ProperIncludes
(deftest compile-proper-included-in-test
  (unsupported-binary-operand "ProperIncludedIn"))


;; 19.28. Size
;;
;; The Size operator returns the size of an interval.
;;
;; The result of this operator is equivalent to invoking: End(i) - Start(i) +
;; point-size, where the point-size for the point type of the interval is
;; determined by: Successor(Minimum_T) - Minimum_T.
;;
;; Note that this operator is not defined for intervals of type Date, DateTime,
;; and Time.
;;
;; If the argument is null, the result is null.
;;
;; TODO: I don't get it


;; 19.29. Start
;;
;; The Start operator returns the starting point of an interval.
;;
;; If the low boundary of the interval is open, this operator returns the
;; Successor of the low value of the interval. Note that if the low value of
;; the interval is null, the result is null.
;;
;; If the low boundary of the interval is closed and the low value of the
;; interval is not null, this operator returns the low value of the interval.
;; Otherwise, the result is the minimum value of the point type of the interval.
;;
;; If the argument is null, the result is null.
(deftest compile-start-test
  (testing "Null"
    (are [x res] (= res (-eval (compile {} {:type "Start" :operand x}) {} nil nil))
      {:type "Null"} nil))

  (testing "Integer"
    (are [x res] (= res (-eval (compile {} {:type "Start" :operand x}) {} nil nil))
      #elm/interval [#elm/integer "1" #elm/integer "2"] 1
      #elm/interval [:< #elm/integer "1" #elm/integer "2"] 2
      #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/integer "2"] Integer/MIN_VALUE))

  (testing "Decimal"
    (are [x res] (= res (-eval (compile {} {:type "Start" :operand x}) {} nil nil))
      #elm/interval [#elm/decimal "1.1" #elm/decimal "2"] 1.1M
      #elm/interval [:< #elm/decimal "1.1" #elm/decimal "2"] 1.10000001M
      #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Decimal"} #elm/decimal "2"] decimal/min)))


;; 19.30. Starts
;;
;; The Starts operator returns true if the first interval starts the second. In
;; other words, if the starting point of the first is equal to the starting
;; point of the second interval and the ending point of the first interval is
;; less than or equal to the ending point of the second interval.
;;
;; This operator uses the semantics described in the Start and End operators to
;; determine interval boundaries.
;;
;; If precision is specified and the point type is a Date, DateTime, or Time
;; type, comparisons used in the operation are performed at the specified
;; precision.
;;
;; If either argument is null, the result is null.
(deftest compile-starts-test
  (testing "Null"
    (are [a b res] (= res (-eval (compile {} {:type "Starts" :operand [a b]}) {} nil nil))
      {:type "Null"} #elm/interval [#elm/integer "1" #elm/integer "2"] nil
      #elm/interval [#elm/integer "1" #elm/integer "2"] {:type "Null"} nil))

  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} {:type "Starts" :operand [a b]}) {} nil nil))
      #elm/interval [#elm/integer "1" #elm/integer "3"]
      #elm/interval [#elm/integer "1" #elm/integer "3"] true
      #elm/interval [#elm/integer "1" #elm/integer "2"]
      #elm/interval [#elm/integer "1" #elm/integer "3"] true
      #elm/interval [#elm/integer "2" #elm/integer "3"]
      #elm/interval [#elm/integer "1" #elm/integer "3"] false)))


;; 19.31. Union
;;
;; The Union operator returns the union of its arguments.
;;
;; This operator has two overloads: List Interval
;;
;; For the list overload, this operator returns a list with all unique elements
;; from both arguments.
;;
;; For the interval overload, this operator returns the interval that starts at
;; the earliest starting point in either argument, and ends at the latest
;; starting point in either argument. If the arguments do not overlap or meet,
;; this operator returns null.
;;
;; If either argument is null, the result is null.
;;
;; TODO: only implemented as binary operator because it's binary in CQL.
(deftest compile-union-test
  (testing "List"
    (are [x y res] (= res (-eval (compile {} (elm/union [x y])) {} nil nil))
      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] [nil nil]
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "1"] [1]
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "2"] [1 2]
      #elm/list [#elm/integer "1"] #elm/list [#elm/integer "1" #elm/integer "2"] [1 2]

      {:type "Null"} {:type "Null"} nil))

  (testing "Interval"
    (are [x y res] (= res (-eval (compile {} (elm/union [x y])) {} nil nil))
      #elm/interval [#elm/integer "1" #elm/integer "2"]
      #elm/interval [#elm/integer "3" #elm/integer "4"]
      (interval 1 4)

      {:type "Null"} {:type "Null"} nil)))


;; 19.32. Width
;;
;; The Width operator returns the width of an interval. The result of this
;; operator is equivalent to invoking: End(i) - Start(i).
;;
;; Note that this operator is not defined for intervals of type Date, DateTime,
;; and Time.
;;
;; If the argument is null, the result is null.
(deftest compile-width-test
  (testing "Null"
    (are [x res] (= res (-eval (compile {} {:type "Width" :operand x}) {} nil nil))
      {:type "Null"} nil))

  (testing "Integer"
    (are [x res] (= res (-eval (compile {} {:type "Width" :operand x}) {} nil nil))
      #elm/interval [#elm/integer "1" #elm/integer "2"] 1)))



;; 20. List Operators

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
  (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
    #elm/list []
    []

    #elm/list [{:type "Null"}]
    [nil]

    #elm/list [#elm/integer "1"]
    [1]

    #elm/list [#elm/integer "1" {:type "Null"}]
    [1 nil]

    #elm/list [#elm/integer "1" #elm/integer "2"]
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
  (are [a] (= a (-eval (compile {} {:type "Current"}) {} nil a))
    1)

  (are [a] (= a (-eval (compile {} {:type "Current" :scope "A"}) {} nil {"A" a}))
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
  (are [list res] (= res (-eval (compile {} (elm/distinct list)) {} nil nil))
    #elm/list [#elm/integer "1"] [1]
    #elm/list [#elm/integer "1" #elm/integer "1"] [1]
    #elm/list [#elm/integer "1" #elm/integer "1" #elm/integer "2"] [1 2]
    #elm/list [{:type "Null"}] [nil]
    #elm/list [{:type "Null"} {:type "Null"}] [nil nil]
    #elm/list [{:type "Null"} {:type "Null"} {:type "Null"}] [nil nil nil]
    #elm/list [#elm/quantity [100 "cm"] #elm/quantity [1 "m"]] [(quantity 100 "cm")]
    #elm/list [#elm/quantity [1 "m"] #elm/quantity [100 "cm"]] [(quantity 1 "m")]

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
  (are [list res] (= res (-eval (compile {} (elm/exists list)) {} nil nil))
    #elm/list [#elm/integer "1"] true
    #elm/list [#elm/integer "1" #elm/integer "1"] true
    #elm/list [] false

    {:type "Null"} false))


;; 20.9. Filter
;;
;; The Filter operator returns a list with only those elements in the source
;; list for which the condition element evaluates to true.
;;
;; If the source argument is null, the result is null.
(deftest compile-filter-test
  (are [source condition res] (= res (-eval (compile {} {:type "Filter" :source source :condition condition :scope "A"}) {} nil nil))
    #elm/list [#elm/integer "1"] #elm/boolean "false" []
    #elm/list [#elm/integer "1"] #elm/equal [#elm/current "A" #elm/integer "1"] [1]

    {:type "Null"} #elm/boolean "true" nil))


;; 20.10. First
;;
;; The First operator returns the first element in a list. If the order by
;; attribute is specified, the list is sorted by that ordering prior to
;; returning the first element.
;;
;; If the argument is null, the result is null.
(deftest compile-first-test
  (are [source res] (= res (-eval (compile {} {:type "First" :source source}) {} nil nil))
    #elm/list [#elm/integer "1"] 1
    #elm/list [#elm/integer "1" #elm/integer "2"] 1

    {:type "Null"} nil))


;; 20.11. Flatten
;;
;; The Flatten operator flattens a list of lists into a single list.
;;
;; If the argument is null, the result is null.
(deftest compile-flatten-test
  (are [list res] (= res (-eval (compile {} (elm/flatten list)) {} nil nil))
    #elm/list [] []
    #elm/list [#elm/integer "1"] [1]
    #elm/list [#elm/integer "1" #elm/list [#elm/integer "2"]] [1 2]
    #elm/list [#elm/integer "1" #elm/list [#elm/integer "2"] #elm/integer "3"] [1 2 3]
    #elm/list [#elm/integer "1" #elm/list [#elm/integer "2" #elm/list [#elm/integer "3"]]] [1 2 3]
    #elm/list [#elm/list [#elm/integer "1" #elm/list [#elm/integer "2"]] #elm/integer "3"] [1 2 3]

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
    (are [source element res] (= res (-eval (compile {} {:type "ForEach" :source source :element element}) {} nil nil))
      #elm/list [#elm/integer "1"] {:type "Null"} [nil]

      {:type "Null"} {:type "Null"} nil))

  (testing "With scope"
    (are [source element res] (= res (-eval (compile {} {:type "ForEach" :source source :element element :scope "A"}) {} nil nil))
      #elm/list [#elm/integer "1"] #elm/current "A" [1]
      #elm/list [#elm/integer "1" #elm/integer "2"] #elm/add [#elm/current "A" #elm/integer "1"] [2 3]

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
  (are [source element res] (= res (-eval (compile {} {:type "IndexOf" :source source :element element}) {} nil nil))
    #elm/list [] #elm/integer "1" -1
    #elm/list [#elm/integer "1"] #elm/integer "1" 0
    #elm/list [#elm/integer "1" #elm/integer "1"] #elm/integer "1" 0
    #elm/list [#elm/integer "1" #elm/integer "2"] #elm/integer "2" 1

    #elm/list [] {:type "Null"} nil
    {:type "Null"} #elm/integer "1" nil
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
  (are [source res] (= res (-eval (compile {} {:type "Last" :source source}) {} nil nil))
    #elm/list [#elm/integer "1"] 1
    #elm/list [#elm/integer "1" #elm/integer "2"] 2

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
  (are [list res] (= res (-eval (compile {} (elm/singleton-from list)) {} nil nil))
    #elm/list [] nil
    #elm/list [#elm/integer "1"] 1
    {:type "Null"} nil)

  (are [list] (thrown? Exception (-eval (compile {} (elm/singleton-from list)) {} nil nil))
    #elm/list [#elm/integer "1" #elm/integer "1"]))


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
  (are [source start end res] (= res (-eval (compile {} {:type "Slice" :source source :startIndex start :endIndex end}) {} nil nil))
    #elm/list [#elm/integer "1"] #elm/integer "0" #elm/integer "1" [1]
    #elm/list [#elm/integer "1" #elm/integer "2"] #elm/integer "0" #elm/integer "1" [1]
    #elm/list [#elm/integer "1" #elm/integer "2"] #elm/integer "1" #elm/integer "2" [2]
    #elm/list [#elm/integer "1" #elm/integer "2" #elm/integer "3"] #elm/integer "1" #elm/integer "3" [2 3]
    #elm/list [#elm/integer "1" #elm/integer "2"] {:type "Null"} {:type "Null"} [1 2]

    #elm/list [#elm/integer "1"] #elm/integer "-1" #elm/integer "0" []
    #elm/list [#elm/integer "1"] #elm/integer "1" #elm/integer "0" []


    {:type "Null"} #elm/integer "0" #elm/integer "0" nil
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
  (are [source by res] (= res (-eval (compile {} {:type "Sort" :source source :by [by]}) {} nil nil))
    #elm/list [#elm/integer "2" #elm/integer "1"]
    {:type "ByDirection" :direction "asc"} [1 2]
    #elm/list [#elm/integer "1" #elm/integer "2"]
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



;; 21. Aggregate Operators

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
    (are [source res] (= res (-eval (compile {} {:type "AllTrue" :source source}) {} nil nil))
      #elm/list [#elm/boolean "true" #elm/boolean "false"] false
      #elm/list [#elm/boolean "false"] false
      #elm/list [#elm/boolean "true"] true
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
    (are [source res] (= res (-eval (compile {} {:type "AnyTrue" :source source}) {} nil nil))
      #elm/list [#elm/boolean "true" #elm/boolean "false"] true
      #elm/list [#elm/boolean "false"] false
      #elm/list [#elm/boolean "true"] true

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
    (are [source res] (= res (-eval (compile {} {:type "Avg" :source source}) {} nil nil))
      #elm/list [#elm/decimal "1" #elm/decimal "2"] 1.5M
      #elm/list [#elm/integer "1" #elm/integer "2"] 1.5M
      #elm/list [#elm/integer "1"] 1M

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
    (are [source res] (= res (-eval (compile {} {:type "Count" :source source}) {} nil nil))
      #elm/list [#elm/integer "1"] 1
      #elm/list [#elm/integer "1" #elm/integer "1"] 2

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
    (are [source res] (= res (-eval (compile {} {:type "GeometricMean" :source source}) {} nil nil))
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 4M
      #elm/list [#elm/integer "2" #elm/integer "8"] 4M
      #elm/list [#elm/integer "1"] 1M

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
    (are [source res] (= res (-eval (compile {} {:type "Product" :source source}) {} nil nil))
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 16M
      #elm/list [#elm/integer "2" #elm/integer "8"] 16
      #elm/list [#elm/integer "1"] 1

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
    (are [source res] (= res (-eval (compile {} {:type "Max" :source source}) {} nil nil))
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 8M
      #elm/list [#elm/integer "2" #elm/integer "8"] 8
      #elm/list [#elm/integer "1"] 1

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
    (are [source res] (= res (-eval (compile {} {:type "Median" :source source}) {} nil nil))
      #elm/list [#elm/decimal "2" #elm/decimal "10" #elm/decimal "8"] 8M
      #elm/list [#elm/integer "2" #elm/integer "10" #elm/integer "8"] 8
      #elm/list [#elm/integer "1" #elm/integer "2"] 1.5M
      #elm/list [#elm/integer "1"] 1

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
    (are [source res] (= res (-eval (compile {} {:type "Min" :source source}) {} nil nil))
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 2M
      #elm/list [#elm/integer "2" #elm/integer "8"] 2
      #elm/list [#elm/integer "1"] 1

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
    (are [source res] (= res (-eval (compile {} {:type "Mode" :source source}) {} nil nil))
      #elm/list [#elm/decimal "2" #elm/decimal "2" #elm/decimal "8"] 2M
      #elm/list [#elm/integer "2" #elm/integer "2" #elm/integer "8"] 2
      #elm/list [#elm/integer "1"] 1
      #elm/list [#elm/integer "1" {:type "Null"} {:type "Null"}] 1

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
    (are [source res] (= res (-eval (compile {} {:type "PopulationVariance" :source source}) {} nil nil))
      #elm/list [#elm/decimal "1" #elm/decimal "2" #elm/decimal "3" #elm/decimal "4" #elm/decimal "5"] 2M

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
    (are [source res] (= res (-eval (compile {} {:type "PopulationStdDev" :source source}) {} nil nil))
      #elm/list [#elm/decimal "1" #elm/decimal "2" #elm/decimal "3" #elm/decimal "4" #elm/decimal "5"] 1.41421356M

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
    (are [source res] (= res (-eval (compile {} {:type "Sum" :source source}) {} nil nil))
      #elm/list [#elm/decimal "2" #elm/decimal "8"] 10M
      #elm/list [#elm/integer "2" #elm/integer "8"] 10
      #elm/list [#elm/integer "1"] 1

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
    (are [source res] (= res (-eval (compile {} {:type "StdDev" :source source}) {} nil nil))
      #elm/list [#elm/decimal "1" #elm/decimal "2" #elm/decimal "3" #elm/decimal "4" #elm/decimal "5"] 1.58113883M

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
    (are [source res] (= res (-eval (compile {} {:type "Variance" :source source}) {} nil nil))
      #elm/list [#elm/decimal "1" #elm/decimal "2" #elm/decimal "3" #elm/decimal "4" #elm/decimal "5"] 2.5M

      #elm/list [{:type "Null"}] nil
      #elm/list [] nil
      {:type "Null"} nil)))



;; 22. Type Operators

;; 22.1. As
;;
;; The As operator allows the result of an expression to be cast as a given
;; target type. This allows expressions to be written that are statically typed
;; against the expected run-time type of the argument. If the argument is not of
;; the specified type and the strict attribute is false (the default) the
;; result is null. If the argument is not of the specified type and the strict
;; attribute is true an exception is thrown.
(deftest compile-as-test
  (testing "FHIR types"
    (are [elm resource res] (= res (-eval (compile {} elm) {} nil {"R" resource}))
      #elm/as["{http://hl7.org/fhir}boolean"
              {:path "deceased"
               :scope "R"
               :type "Property"}]
      (with-meta
        {:resourceType "Patient" :id "0" :deceasedBoolean true}
        {:type :fhir/Patient})
      true

      #elm/as ["{http://hl7.org/fhir}integer"
               {:path "value"
                :scope "R"
                :type "Property"}]
      (with-meta
        {:resourceType "Observation" :valueInteger 1}
        {:type :fhir/Observation})
      1

      #elm/as ["{http://hl7.org/fhir}string"
               {:path "name"
                :scope "R"
                :type "Property"}]
      (with-meta
        {:resourceType "Account" :name "a"}
        {:type :fhir/Account})
      "a"

      #elm/as ["{http://hl7.org/fhir}decimal"
               {:path "duration"
                :scope "R"
                :type "Property"}]
      (with-meta
        {:resourceType "Media" :duration 1.1M}
        {:type :fhir/Media})
      1.1M

      #elm/as ["{http://hl7.org/fhir}uri"
               {:path "url"
                :scope "R"
                :type "Property"}]
      (with-meta
        {:resourceType "Measure" :url "a"}
        {:type :fhir/Measure})
      "a"

      #elm/as ["{http://hl7.org/fhir}url"
               {:path "address"
                :scope "R"
                :type "Property"}]
      (with-meta
        {:resourceType "Endpoint" :address "a"}
        {:type :fhir/Endpoint})
      "a"

      #elm/as ["{http://hl7.org/fhir}dateTime"
               {:path "value"
                :scope "R"
                :type "Property"}]
      (with-meta
        {:resourceType "Observation" :valueDateTime "2019-09-04"}
        {:type :fhir/Observation})
      "2019-09-04"

      #elm/as ["{http://hl7.org/fhir}Quantity"
               {:path "value"
                :scope "R"
                :type "Property"}]
      (with-meta
        {:resourceType "Observation" :valueDateTime "2019-09-04"}
        {:type :fhir/Observation})
      nil))

  (testing "ELM types"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/as ["{urn:hl7-org:elm-types:r1}Boolean" #elm/boolean "true"]
      true

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" #elm/integer "1"]
      1

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
      nil

      #elm/as ["{urn:hl7-org:elm-types:r1}DateTime" #elm/date-time "2019-09-04"]
      (LocalDate/of 2019 9 4))))


;; TODO 22.2. CanConvert


;; TODO 22.3. CanConvertQuantity


;; 22.4. Children
;;
;; For structured types, the Children operator returns a list of all the values
;; of the elements of the type. List-valued elements are expanded and added to
;; the result individually, rather than as a single list.
;;
;; For list types, the result is the same as invoking Children on each element
;; in the list and flattening the resulting lists into a single result.
;;
;; If the source is null, the result is null.
(deftest compile-to-children-test
  (testing "Code"
    (are [elm res] (= res (-eval (compile {} #elm/children elm) {:now now} nil nil))
      (code "system-134534" "code-134551")
      ["code-134551" nil "system-134534" nil]))

  ;; TODO: other types

  (testing-unary-null elm/children))


;; TODO 22.5. Convert


;; 22.6. ConvertQuantity
;;
;; The ConvertQuantity operator converts a Quantity to an equivalent Quantity
;; with the given unit. If the unit of the input quantity can be converted to
;; the target unit, the result is an equivalent Quantity with the target unit.
;; Otherwise, the result is null.
;;
;; Note that implementations are not required to support quantity conversion.
;; Implementations that do support unit conversion shall do so according to the
;; conversion specified by UCUM. Implementations that do not support unit
;; conversion shall throw an error if an unsupported unit conversion is
;; requested with this operation.
;;
;; If either argument is null, the result is null.
(deftest compile-convert-quantity-test
  (are [argument unit res] (= res (-eval (compile {} #elm/convert-quantity [argument unit]) {} nil nil))
    #elm/quantity [5 "mg"] #elm/string "g" (quantity 0.005 "g")
    #elm/quantity [5 "mg"] #elm/string "m" nil)

  (testing-binary-null elm/convert-quantity #elm/quantity [5 "mg"] #elm/string "m"))


;; TODO 22.7. ConvertsToBoolean


;; TODO 22.8. ConvertsToDate


;; TODO 22.9. ConvertsToDateTime


;; TODO 22.10. ConvertsToDecimal


;; TODO 22.11. ConvertsToInteger


;; TODO 22.12. ConvertsToQuantity


;; TODO 22.13. ConvertsToRatio


;; TODO 22.14. ConvertsToString


;; TODO 22.15. ConvertsToTime


;; 22.16. Descendents
;;
;; For structured types, the Descendents operator returns a list of all the
;; values of the elements of the type, recursively. List-valued elements are
;; expanded and added to the result individually, rather than as a single list.
;;
;; For list types, the result is the same as invoking Descendents on each
;; element in the list and flattening the resulting lists into a single result.
;;
;; If the source is null, the result is null.
(deftest compile-to-descendents-test
  (testing "Code"
    (are [elm res] (= res (-eval (compile {} #elm/descendents elm) {:now now} nil nil))
      (code "system-134534" "code-134551")
      ["code-134551" nil "system-134534" nil]))

  ;; TODO: other types

  (testing-unary-null elm/descendents))


;; TODO 22.17. Is


;; TODO 22.18. ToBoolean


;; TODO 22.19. ToChars


;; TODO 22.20. ToConcept


;; 22.21. ToDate
;;
;; The ToDate operator converts the value of its argument to a Date value.
;;
;; For String values, The operator expects the string to be formatted using the
;; ISO-8601 date representation:
;;
;; YYYY-MM-DD
;;
;; In addition, the string must be interpretable as a valid date value.
;;
;; If the input string is not formatted correctly, or does not represent a valid
;; date value, the result is null.
;;
;; As with date literals, date values may be specified to any precision.
;;
;; For DateTime values, the result is equivalent to extracting the Date
;; component of the DateTime value.
;;
;; If the argument is null, the result is null.
(deftest compile-to-date-test
  (testing "String values"
    (are [elm res] (= res (-eval (compile {} #elm/to-date elm) {:now now} nil nil))
      #elm/string "2019" (Year/of 2019)
      #elm/string "2019-01" (YearMonth/of 2019 1)
      #elm/string "2019-01-01" (LocalDate/of 2019 1 1)

      #elm/string "aaaa" nil
      #elm/string "2019-13" nil
      #elm/string "2019-02-29" nil))

  (testing "DateTime values"
    (are [elm res] (= res (-eval (compile {} #elm/to-date elm) {:now now} nil nil))
      #elm/date-time "2019-01-01T12:13" (LocalDate/of 2019 1 1)))

  (testing-unary-null elm/to-date))


;; 22.22. ToDateTime
;;
;; The ToDateTime operator converts the value of its argument to a DateTime
;; value.
;;
;; For String values, the operator expects the string to be formatted using the
;; ISO-8601 datetime representation:
;;
;; YYYY-MM-DDThh:mm:ss.fff(+|-)hh:mm footnote:formatting-strings[]
;;
;; In addition, the string must be interpretable as a valid DateTime value.
;;
;; If the input string is not formatted correctly, or does not represent a
;; valid DateTime value, the result is null.
;;
;; As with Date and Time literals, DateTime values may be specified to any
;; precision. If no timezone offset is supplied, the timezone offset of the
;; evaluation request timestamp is assumed.
;;
;; For Date values, the result is a DateTime with the time components
;; unspecified, except the timezone offset, which is set to the timezone offset
;; of the evaluation request timestamp.
;;
;; If the argument is null, the result is null.
(deftest compile-to-date-time-test
  (testing "String values"
    (are [elm res] (= res (-eval (compile {} #elm/to-date-time elm) {:now now} nil nil))
      #elm/string "2020"
      (Year/of 2020)

      #elm/string "2020-03"
      (YearMonth/of 2020 3)

      #elm/string "2020-03-08"
      (LocalDate/of 2020 3 8)

      #elm/string "2020-03-08T12:54:00"
      (LocalDateTime/of 2020 3 8 12 54)

      #elm/string "2020-03-08T12:54:00+00:00"
      (LocalDateTime/of 2020 3 8 12 54)))

  (testing "ELM types"
    (are [elm res] (= res (-eval (compile {} #elm/to-date-time elm) {:now now} nil nil))
      #elm/date "2019"
      (Year/of 2019)))

  (testing-unary-null elm/to-date-time))


;; 22.23. ToDecimal
;;
;; The ToDecimal operator converts the value of its argument to a Decimal value.
;; The operator accepts strings using the following format:
;;
;; (+|-)?#0(.0#)?
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none), followed by at least one digit, followed optionally by a
;; decimal point, at least one digit, and any number of additional digits
;; (including none).
;;
;; Note that the decimal value returned by this operator must be limited in
;; precision and scale to the maximum precision and scale representable for
;; Decimal values within CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Decimal value, the result is null.
;;
;; If the argument is null, the result is null.
(deftest compile-to-decimal-test
  (testing "String values"
    (are [elm res] (= res (-eval (compile {} #elm/to-decimal elm) {} nil nil))
      (elm/string (str decimal/min)) decimal/min
      #elm/string "-1.1" -1.1M
      #elm/string "-1" -1M
      #elm/string "0" 0M
      #elm/string "1" 1M
      (elm/string (str decimal/max)) decimal/max

      (elm/string (str (- decimal/min 1e-8M))) nil
      (elm/string (str (+ decimal/max 1e-8M))) nil
      #elm/string "a" nil))

  (testing-unary-null elm/to-decimal))


;; 22.24. ToInteger
;;
;; The ToInteger operator converts the value of its argument to an Integer
;; value. The operator accepts strings using the following format:
;;
;; (+|-)?#0
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none), followed by at least one digit.
;;
;; Note that the integer value returned by this operator must be a valid value
;; in the range representable for Integer values in CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as
;; a valid Integer value, the result is null.
;;
;; If the argument is null, the result is null.
(deftest compile-to-integer-test
  (testing "String values"
    (are [elm res] (= res (-eval (compile {} #elm/to-integer elm) {} nil nil))
      (elm/string (str Integer/MIN_VALUE)) Integer/MIN_VALUE
      #elm/string "-1" -1
      #elm/string "0" 0
      #elm/string "1" 1
      (elm/string (str Integer/MAX_VALUE)) Integer/MAX_VALUE

      (elm/string (str (dec Integer/MIN_VALUE))) nil
      (elm/string (str (inc Integer/MAX_VALUE))) nil
      #elm/string "a" nil))

  (testing-unary-null elm/to-integer))


;; 22.25. ToList
;;
;; The ToList operator returns its argument as a List value. The operator
;; accepts a singleton value of any type and returns a list with the value as
;; the single element.
;;
;; If the argument is null the operator returns an empty list.
;;
;; The operator is effectively shorthand for "if operand is null then { } else
;; { operand }".
;;
;; The operator is used to implement list promotion efficiently.
(deftest compile-to-list-test
  (are [elm res] (= res (-eval (compile {} #elm/to-list elm) {} nil nil))
    {:type "Null"}
    []

    #elm/boolean "false"
    [false]

    #elm/integer "1"
    [1]))


;; 22.26. ToQuantity
;;
;; The ToQuantity operator converts the value of its argument to a Quantity
;; value. The operator may be used with Integer, Decimal, Ratio, or String
;; values.
;;
;; For String values, the operator accepts strings using the following format:
;;
;; (+|-)?#0(.0#)?('<unit>')?
;;
;; Meaning an optional polarity indicator, followed by any number of digits
;; (including none) followed by at least one digit, optionally followed by a
;; decimal point, at least one digit, and any number of additional digits, all
;; optionally followed by a unit designator as a string literal specifying a
;; valid UCUM unit of measure. Spaces are allowed between the quantity value and
;; the unit designator.
;;
;; Note that the decimal value of the quantity returned by this operator must be
;; a valid value in the range representable for Decimal values in CQL.
;;
;; If the input string is not formatted correctly, or cannot be interpreted as a
;; valid Quantity value, the result is null.
;;
;; For Integer and Decimal values, the result is a Quantity with the value of
;; the integer or decimal input, and the default unit ('1').
;;
;; For Ratio values, the operation is equivalent to the result of dividing the
;; numerator of the ratio by the denominator.
;;
;; If the argument is null, the result is null.
(deftest compile-to-quantity-test
  (testing "String values"
    (are [elm res] (= res (-eval (compile {} #elm/to-quantity elm) {} nil nil))
      #elm/string "1" (quantity 1 "1")

      #elm/string "1'm'" (quantity 1 "m")
      #elm/string "1 'm'" (quantity 1 "m")
      #elm/string "1  'm'" (quantity 1 "m")

      #elm/string "10 'm'" (quantity 10 "m")

      #elm/string "1.1 'm'" (quantity 1.1M "m")

      #elm/string "" nil
      #elm/string "a" nil))

  (testing "Integer values"
    (are [elm res] (= res (-eval (compile {} #elm/to-quantity elm) {} nil nil))
      #elm/integer "1" (quantity 1 "1")))

  (testing "Decimal values"
    (are [elm res] (= res (-eval (compile {} #elm/to-quantity elm) {} nil nil))
      #elm/decimal "1" (quantity 1 "1")
      #elm/decimal "1.1" (quantity 1.1M "1")))

  ;; TODO: Ratio

  (testing-unary-null elm/to-quantity))


;; TODO 22.27. ToRatio


;; 22.28. ToString
;;
;; The ToString operator converts the value of its argument to a String value.
;; The operator uses the following string representations for each type:
;;
;; Boolean  true/false
;; Integer  (-)?#0
;; Decimal  (-)?#0.0#
;; Quantity (-)?#0.0# '<unit>'
;; Date     YYYY-MM-DD
;; DateTime YYYY-MM-DDThh:mm:ss.fff(+|-)hh:mm
;; Time     hh:mm:ss.fff
;; Ratio    <quantity>:<quantity>
;;
;; If the argument is null, the result is null.
(deftest compile-to-string-test
  (testing "Boolean values"
    (are [elm res] (= res (-eval (compile {} #elm/to-string elm) {} nil nil))
      #elm/boolean "true" "true"
      #elm/boolean "false" "false"))

  (testing "Integer values"
    (are [elm res] (= res (-eval (compile {} #elm/to-string elm) {} nil nil))
      #elm/integer "-1" "-1"
      #elm/integer "0" "0"
      #elm/integer "1" "1"))

  (testing "Decimal values"
    (are [elm res] (= res (-eval (compile {} #elm/to-string elm) {} nil nil))
      #elm/decimal "-1" "-1"
      #elm/decimal "0" "0"
      #elm/decimal "1" "1"

      #elm/decimal "-1.1" "-1.1"
      #elm/decimal "0.0" "0.0"
      #elm/decimal "1.1" "1.1"

      #elm/decimal "0.0001" "0.0001"
      #elm/decimal "0.00001" "0.00001"
      #elm/decimal "0.000001" "0.000001"
      #elm/decimal "0.0000001" "0.0000001"
      #elm/decimal "0.00000001" "0.00000001"
      #elm/decimal "0.000000001" "0.00000000"
      #elm/decimal "0.000000005" "0.00000001"))

  (testing "Quantity values"
    (are [elm res] (= res (-eval (compile {} #elm/to-string elm) {} nil nil))
      #elm/quantity [1 "m"] "1 'm'"
      #elm/quantity [1M "m"] "1 'm'"
      #elm/quantity [1.1M "m"] "1.1 'm'"))

  (testing "Date values"
    (are [elm res] (= res (-eval (compile {} #elm/to-string elm) {} nil nil))
      #elm/date "2019" "2019"
      #elm/date "2019-01" "2019-01"
      #elm/date "2019-01-01" "2019-01-01"))

  (testing "DateTime values"
    (are [elm res] (= res (-eval (compile {} #elm/to-string elm) {} nil nil))
      #elm/date-time "2019-01-01T01:00" "2019-01-01T01:00"))

  (testing "Time values"
      #elm/time "01:00" "01:00")

  ;; TODO: Ratio

  (testing-unary-null elm/to-string))


;; TODO 22.29. ToTime



;; 23. Clinical Operators

;; TODO 23.1. AnyInCodeSystem


;; TODO 23.2. AnyInValueSet


;; 23.3. CalculateAge
;;
;; Normalized to CalculateAgeAt
(deftest compile-calculate-age-test
  (unsupported-unary-operand "CalculateAge"))


;; 23.4. CalculateAgeAt
;;
;; Calculates the age in the specified precision of a person born on a given
;; date, as of another given date.
;;
;; The CalculateAgeAt operator has two signatures: (Date, Date) (DateTime,
;; DateTime)
;;
;; For the Date overload, precision must be one of year, month, week, or day,
;; and the result is the number of whole calendar periods that have elapsed
;; between the first date and the second date.
;;
;; For the DateTime overload, the result is the number of whole calendar periods
;; that have elapsed between the first datetime and the second datetime.
(deftest compile-calculate-age-at-test
  (testing "Year"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil nil))
      {:type "CalculateAgeAt" :operand [#elm/date "2018" #elm/date "2019"]
       :precision "Year"}
      1
      {:type "CalculateAgeAt" :operand [#elm/date "2018" #elm/date "2018"]
       :precision "Year"}
      0

      {:type "CalculateAgeAt" :operand [#elm/date "2018" #elm/date "2018"]
       :precision "Month"}
      nil))

  (testing-binary-null elm/calculate-age-at #elm/date "2018"))


;; 23.5. Equal


;; 23.6. Equivalent


;; TODO 23.7. InCodeSystem


;; TODO 23.8. InValueSet


;; 23.9. Not Equal


;; TODO 23.10. SubsumedBy


;; TODO 23.11. Subsumes



;; 24. Errors and Messages

;; TODO 24.1. Message
