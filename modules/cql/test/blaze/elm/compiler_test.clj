(ns blaze.elm.compiler-test
  "Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.code :as code]
    [blaze.elm.compiler :refer [compile compile-with-equiv-clause]]
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.compiler.retrieve-test :as retrieve-test]
    [blaze.elm.date-time :refer [local-time local-time? period]]
    [blaze.elm.decimal :as decimal]
    [blaze.elm.interval :refer [interval]]
    [blaze.elm.literals :as elm]
    [blaze.elm.quantity :refer [quantity]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [are deftest is testing use-fixtures]]
    [clojure.test.check :as tc]
    [clojure.test.check.properties :as prop])
  (:import
    [blaze.elm.date_time Period]
    [clojure.core Eduction]
    [java.math BigDecimal]
    [java.time LocalDate LocalDateTime OffsetDateTime Year YearMonth
               ZoneOffset]
    [java.time.temporal Temporal]
    [javax.measure UnconvertibleException])
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


(use-fixtures :each fixture)


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



;; 1. Simple Values

;; 1.1 Literal
(deftest compile-literal-test
  (testing "Boolean Literal"
    (are [elm res] (= res (compile {} elm))
      #elm/boolean "true" true
      #elm/boolean "false" false))

  (testing "Decimal Literal"
    (are [elm res] (= res (compile {} elm))
      #elm/dec "-1" -1M
      #elm/dec "0" 0M
      #elm/dec "1" 1M

      #elm/dec "-0.1" -0.1M
      #elm/dec "0.0" 0M
      #elm/dec "0.1" 0.1M

      #elm/dec "0.000000001" 0M
      #elm/dec "0.000000005" 1E-8M))

  (testing "Integer Literal"
    (are [elm res] (= res (compile {} elm))
      #elm/int "-1" -1
      #elm/int "0" 0
      #elm/int "1" 1)))



;; 2. Structured Values

;; 2.1. Tuple
(deftest compile-tuple-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
    #elm/tuple {"id" #elm/int "1"}
    {:id 1}

    #elm/tuple {"id" #elm/int "1" "name" #elm/string "john"}
    {:id 1 :name "john"}))


;; 2.3. Property
(deftest compile-property-test
  (testing "with entity supplied over query context"
    (are [elm entity result]
      (= result (-eval (compile {:eval-context "Unspecified"} elm)
                       nil nil {"P" entity}))
      {:path "gender"
       :scope "P"
       :type "Property"
       :resultTypeName "{http://hl7.org/fhir}AdministrativeGender"
       :life/source-type "{http://hl7.org/fhir}Patient"}
      {:Patient/gender "male"}
      "male"))

  (testing "with entity supplied directly"
    (are [elm entity result]
      (= result (-eval (compile {:eval-context "Unspecified"
                                 :life/single-query-scope "P"}
                                elm)
                       nil nil entity))
      {:path "gender"
       :scope "P"
       :type "Property"
       :resultTypeName "{http://hl7.org/fhir}AdministrativeGender"
       :life/source-type "{http://hl7.org/fhir}Patient"}
      {:Patient/gender "male"}
      "male"))

  (testing "with source"
    (are [elm source result]
      (= result (-eval (compile {:eval-context "Patient"} elm)
                       {:library-context {"Patient" source}} nil nil))
      {:path "gender"
       :source {:name "Patient" :type "ExpressionRef"}
       :type "Property"
       :resultTypeName "{http://hl7.org/fhir}AdministrativeGender"
       :life/source-type "{http://hl7.org/fhir}Patient"}
      {:Patient/gender "male"}
      "male"))

  (testing "with Tuple source"
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
        [{:name "id" :value #elm/int "1"}]}}
      1)))



;; 3. Clinical Values

(defn stub-to-code [system version-spec code result]
  (st/instrument
    [`code/to-code]
    {:spec
     {`code/to-code
      (s/fspec
        :args
        (s/cat :system #{system} :version version-spec :code #{code})
        :ret #{result})}
     :stub
     #{`code/to-code}}))

;; 3.1. Code
;;
;; The Code type represents a literal code selector.
(deftest compile-code-test
  (testing "without version"
    (stub-to-code "life" nil? "0" ::code)

    (let [context
          {:db ::db
           :library
           {:codeSystems {:def [{:name "life" :id "life"}]}}}
          code
          {:type "Code"
           :system {:name "life"}
           :code "0"}]
      (is (= ::code (-eval (compile context code) {:db ::db} nil nil)))))

  (testing "with-version"
    (stub-to-code "life" #{"v1"} "0" ::code)

    (let [context
          {:db ::db
           :library
           {:codeSystems {:def [{:name "life" :id "life" :version "v1"}]}}}
          code
          {:type "Code"
           :system {:name "life"}
           :code "0"}]
      (is (= ::code (-eval (compile context code) {:db ::db} nil nil))))))


;; 3.3. CodeRef
;;
;; The CodeRef expression allows a previously defined code to be referenced
;; within an expression.
(deftest compile-code-ref-test
  (testing "without version"
    (stub-to-code "life" nil? "0" ::code)
    (let [context
          {:db ::db
           :library
           {:codeSystems {:def [{:name "life" :id "life"}]}
            :codes
            {:def
             [{:name "lens_0" :id "0" :codeSystem {:name "life"}}]}}}]
      (are [name result] (= result (-eval (compile context {:type "CodeRef" :name name}) {:db ::db} nil nil))
        "lens_0"
        ::code)))

  (testing "with version"
    (stub-to-code "life" #{"v1"} "0" ::code)
    (let [context
          {:db ::db
           :library
           {:codeSystems {:def [{:name "life" :id "life" :version "v1"}]}
            :codes
            {:def
             [{:name "lens_0" :id "0" :codeSystem {:name "life"}}]}}}]
      (are [name result] (= result (-eval (compile context {:type "CodeRef" :name name}) {:db ::db} nil nil))
        "lens_0"
        ::code))))


;; 3.9. Quantity
;;
;; The Quantity type defines a clinical quantity. For example, the quantity 10
;; days or 30 mmHg. The value is a decimal, while the unit is expected to be a
;; valid UCUM unit.
(deftest compile-quantity-test
  (testing "Examples"
    (are [elm res] (= res (compile {} elm))
      #elm/quantity [1] 1
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
(deftest compile-expression-ref-test
  (are [elm res]
    (= res (-eval (compile {} elm) {:library-context {"foo" ::result}} nil nil))
    {:type "ExpressionRef" :name "foo"}
    ::result))


;; 9.4. FunctionRef
(deftest compile-function-ref-test
  (are [elm res]
    (= res (-eval (compile {} elm) {} nil nil))
    {:name "ToString"
     :libraryName "FHIRHelpers"
     :type "FunctionRef"
     :operand [#elm/string "foo"]}
    "foo"))



;; 10. Queries

;; 10.1. Query
;;
;; The Query operator represents a clause-based query. The result of the query
;; is determined by the type of sources included, as well as the clauses used
;; in the query.
(deftest compile-query-test
  (testing "Non-retrieve queries"
    (testing "Sort"
      (are [query res] (= res (-eval (compile {} query) {} nil nil))
        {:type "Query"
         :source
         [{:alias "S"
           :expression #elm/list [#elm/int "2" #elm/int "1" #elm/int "1"]}]
         :sort {:by [{:type "ByDirection" :direction "asc"}]}}
        [1 2]))

    (testing "Return non-distinct"
      (are [query res] (= res (-eval (compile {} query) {} nil nil))
        {:type "Query"
         :source
         [{:alias "S"
           :expression #elm/list [#elm/int "1" #elm/int "1"]}]
         :return {:distinct false :expression {:type "AliasRef" :name "S"}}}
        [1 1]))

    (testing "returns only the first item on optimize first"
      (let [query {:type "Query"
                   :source
                   [{:alias "S"
                     :expression #elm/list [#elm/int "1" #elm/int "1"]}]}
            res (-eval (compile {:optimizations #{:first}} query) {} nil nil)]
        (is (instance? Eduction res)))))

  (testing "Retrieve queries"
    (retrieve-test/stub-expr
      "Unspecified" ::db "Patient" "code" nil?
      (reify Expression
        (-eval [_ _ _ _]
          [::patient])))

    (let [retrieve {:type "Retrieve" :dataType "{http://hl7.org/fhir}Patient"}
          where {:type "Equal"
                 :operand
                 [{:path "value"
                   :scope "P"
                   :type "Property"
                   :resultTypeName "{http://hl7.org/fhir}string"
                   :life/source-type "{http://hl7.org/fhir}Patient"}
                  #elm/int "2"]}
          return {:path "value"
                  :scope "P"
                  :type "Property"
                  :resultTypeName "{http://hl7.org/fhir}string"
                  :life/source-type "{http://hl7.org/fhir}Patient"}]
      (are [query res]
        (= res (-eval (compile {:db ::db :eval-context "Unspecified"} query)
                      {:db ::db} nil nil))
        {:type "Query"
         :source
         [{:alias "P"
           :expression retrieve}]}
        [::patient]

        {:type "Query"
         :source
         [{:alias "P"
           :expression retrieve}]
         :where where}
        []

        {:type "Query"
         :source
         [{:alias "P"
           :expression retrieve}]
         :where #elm/boolean "false"}
        []

        {:type "Query"
         :source
         [{:alias "P"
           :expression retrieve}]
         :return {:expression return}}
        [nil]

        {:type "Query"
         :source
         [{:alias "P"
           :expression retrieve}]
         :where where
         :return {:expression return}}
        []))))


;; 10.3. AliasRef
(deftest compile-alias-ref-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil {"foo" ::result}))
    {:type "AliasRef" :name "foo"}
    ::result))


;; 10.12. With
(deftest compile-with-clause-test
  (st/instrument
    `compile-with-equiv-clause
    {:spec
     {`compile-with-equiv-clause
      (s/fspec
        :args (s/cat :context any? :with-equiv-clause :elm.query.life/with-equiv))}})
  (retrieve-test/stub-expr
    "Unspecified" ::db "Observation" "code" nil?
    (reify Expression
      (-eval [_ _ _ _]
        [{:Observation/subject ::subject}])))

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
          {:db ::db :life/single-query-scope "O0" :eval-context "Unspecified"}
          create-clause (compile-with-equiv-clause compile-context elm)
          eval-context {:db ::db}
          eval-clause (create-clause eval-context nil)
          lhs-entity {:Observation/subject ::subject}]
      (is (true? (eval-clause eval-context nil lhs-entity)))))

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
          {:db ::db :life/single-query-scope "P" :eval-context "Unspecified"}
          create-clause (compile-with-equiv-clause compile-context elm)
          eval-context {:db ::db}
          eval-clause (create-clause eval-context nil)
          lhs-entity ::subject]
      (is (true? (eval-clause eval-context nil lhs-entity))))))



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
  (stub-to-code "life" nil? "0" ::code)

  (let [context
        {:db ::db
         :library
         {:codeSystems {:def [{:name "life" :id "life"}]}
          :codes
          {:def
           [{:name "lens_0" :id "0" :codeSystem {:name "life"}}]}}}]

    (testing "without related context"
      (testing "without codes"
        (retrieve-test/stub-expr ::eval-context ::db "Foo" "code" nil? ::expr)

        (let [elm {:type "Retrieve" :dataType "{http://hl7.org/fhir}Foo"}
              expr (compile (assoc context :eval-context ::eval-context) elm)]
          (is (= ::expr expr))))

      (testing "with codes"
        (retrieve-test/stub-expr
          ::eval-context ::db "Foo" "code" #{[::code]} ::expr)

        (let [elm {:type "Retrieve"
                   :dataType "{http://hl7.org/fhir}Foo"
                   :codes
                   {:type "ToList"
                    :operand {:name "lens_0" :type "CodeRef"}}}
              expr (compile (assoc context :eval-context ::eval-context) elm)]
          (is (= ::expr expr)))))

    (testing "with related context"
      (retrieve-test/stub-with-related-context-expr
        "context-expr" "Foo" "code" nil?
        ::expr)

      (let [elm {:type "Retrieve"
                 :dataType "{http://hl7.org/fhir}Foo"
                 :context #elm/string "context-expr"}
            expr (compile (assoc context :eval-context "Specimen") elm)]
        (is (= ::expr expr))))))



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
;; For quantities, this means that the dimensions of each quantity must be the
;; same, but not necessarily the unit. For example, units of 'cm' and 'm' are
;; comparable, but units of 'cm2' and 'cm' are not. Attempting to operate on
;; quantities with invalid units will result in a run-time error.
;;
;; For tuple types, this means that equality returns true if and only if the
;; tuples are of the same type, and the values for all elements that have
;; values, by name, are equal.
;;
;; For list types, this means that equality returns true if and only if the
;; lists contain elements of the same type, have the same number of elements,
;; and for each element in the lists, in order, the elements are equal using
;; equality semantics.
;;
;; For interval types, equality returns true if and only if the intervals are
;; over the same point type, and they have the same value for the starting and
;; ending points of the interval as determined by the Start and End operators.
;;
;; For date/time values, the comparison is performed by considering each
;; precision in order, beginning with years (or hours for time values). If the
;; values are the same, comparison proceeds to the next precision; if the values
;; are different, the comparison stops and the result is false. If one input
;; has a value for the precision and the other does not, the comparison stops
;; and the result is null; if neither input has a value for the precision or
;; the last precision has been reached, the comparison stops and the result is
;; true.
;;
;; If either argument is null, the result is null.
(deftest compile-equal-test
  (testing "Integer"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/int "1" #elm/int "1" true
      #elm/int "1" #elm/int "2" false

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/dec "1.1" #elm/dec "1.1" true
      #elm/dec "1.1" #elm/dec "2.1" false

      {:type "Null"} #elm/dec "1.1" nil
      #elm/dec "1.1" {:type "Null"} nil))

  (testing "Mixed Integer Decimal"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/int "1" #elm/dec "1" true
      #elm/dec "1" #elm/int "1" true))

  (testing "Mixed Integer String"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/int "1" #elm/string "1" false
      #elm/string "1" #elm/int "1" false))

  (testing "Mixed Decimal String"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/dec "1" #elm/string "1" false
      #elm/string "1" #elm/dec "1" false))

  (testing "Quantity"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/quantity [1] #elm/quantity [1] true
      #elm/quantity [1] #elm/quantity [2] false

      #elm/quantity [1 "s"] #elm/quantity [1 "s"] true
      #elm/quantity [1 "m"] #elm/quantity [1 "m"] true
      #elm/quantity [100 "cm"] #elm/quantity [1 "m"] true
      #elm/quantity [1 "s"] #elm/quantity [2 "s"] false
      #elm/quantity [1 "s"] #elm/quantity [1 "m"] false

      {:type "Null"} #elm/quantity [1] nil
      #elm/quantity [1] {:type "Null"} nil

      {:type "Null"} #elm/quantity [1 "s"] nil
      #elm/quantity [1 "s"] {:type "Null"} nil))

  (testing "Date with year precision"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/date "2013" #elm/date "2013" true
      #elm/date "2012" #elm/date "2013" false
      #elm/date "2013" #elm/date "2012" false

      {:type "Null"} #elm/date "2013" nil
      #elm/date "2013" {:type "Null"} nil))

  (testing "Date with year-month precision"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/date "2013-01" #elm/date "2013-01" true
      #elm/date "2013-01" #elm/date "2013-02" false
      #elm/date "2013-02" #elm/date "2013-01" false

      {:type "Null"} #elm/date "2013-01" nil
      #elm/date "2013-01" {:type "Null"} nil))

  (testing "Date with full precision"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/date "2013-01-01" #elm/date "2013-01-01" true
      #elm/date "2013-01-01" #elm/date "2013-01-02" false
      #elm/date "2013-01-02" #elm/date "2013-01-01" false

      {:type "Null"} #elm/date "2013-01-01" nil
      #elm/date "2013-01-01" {:type "Null"} nil))

  (testing "Date with differing precisions"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/date "2013" #elm/date "2013-01" nil))

  (testing "Today() = Today()"
    (are [a b] (true? (-eval (compile {} (elm/equal [a b])) {:now now} nil nil))
      {:type "Today"} {:type "Today"}))

  (testing "DateTime with full precision (there is only one precision)"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/date-time [#elm/int "2013" #elm/int "1" #elm/int "1"
                      #elm/int "0" #elm/int "0" #elm/int "0"]
      #elm/date-time [#elm/int "2013" #elm/int "1" #elm/int "1"
                      #elm/int "0" #elm/int "0" #elm/int "0"] true
      #elm/date-time [#elm/int "2013" #elm/int "1" #elm/int "1"
                      #elm/int "0" #elm/int "0"]
      #elm/date-time [#elm/int "2013" #elm/int "1" #elm/int "1"
                      #elm/int "0" #elm/int "0" #elm/int "0"] true
      #elm/date-time [#elm/int "2013" #elm/int "1" #elm/int "1"
                      #elm/int "0"]
      #elm/date-time [#elm/int "2013" #elm/int "1" #elm/int "1"
                      #elm/int "0" #elm/int "0" #elm/int "0"] true))

  (testing "Time"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"]
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"] true
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"]
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "16"] false
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "16"]
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"] false

      #elm/time [#elm/int "12" #elm/int "30" #elm/int "0"]
      #elm/time [#elm/int "12" #elm/int "30"] nil

      #elm/time [#elm/int "12" #elm/int "0"]
      #elm/time [#elm/int "12"] nil

      {:type "Null"} #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"] nil
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"] {:type "Null"} nil))

  (testing "List"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/list [#elm/int "1"] #elm/list [#elm/int "1"] true
      #elm/list [] #elm/list [] true

      #elm/list [#elm/int "1"] #elm/list [] false
      #elm/list [#elm/int "1"] #elm/list [#elm/int "2"] false
      #elm/list [#elm/int "1" #elm/int "1"]
      #elm/list [#elm/int "1" #elm/int "2"] false

      #elm/list [#elm/int "1" {:type "Null"}]
      #elm/list [#elm/int "1" {:type "Null"}] nil
      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] nil
      #elm/list [#elm/date "2019"] #elm/list [#elm/date "2019-01"] nil

      {:type "Null"} #elm/list [] nil
      #elm/list [] {:type "Null"} nil))

  (testing "String"
    (are [x y res] (= res (-eval (compile {} (elm/equal [x y])) {} nil nil))
      #elm/string "a" #elm/string "a" true
      #elm/string "a" #elm/string "b" false

      {:type "Null"} #elm/string "a" nil
      #elm/string "a" {:type "Null"} nil))

  (testing "Code"
    (let [ctx
          {:library
           {:codeSystems
            {:def
             [{:name "life" :id "life"}
              {:name "dktk" :id "dktk"}
              {:name "life-2010" :id "life" :version "2010"}
              {:name "life-2020" :id "life" :version "2020"}]}}}]
      (are [a b res] (= res (-eval (compile ctx (elm/equal [a b])) {} nil nil))
        #elm/code ["life" "0"] #elm/code ["life" "0"] true
        #elm/code ["life" "0"] #elm/code ["life" "1"] false
        #elm/code ["life" "0"] #elm/code ["dktk" "0"] false

        #elm/code ["life" "0"] #elm/code ["life-2010" "0"] false
        #elm/code ["life-2010" "0"] #elm/code ["life" "0"] false

        #elm/code ["life-2010" "0"] #elm/code ["life-2020" "0"] false
        #elm/code ["life-2020" "0"] #elm/code ["life-2010" "0"] false

        {:type "Null"} #elm/code ["life" "0"] nil
        #elm/code ["life" "0"] {:type "Null"} nil))))


;; 12.2. Equivalent
;;
;; The Equivalent operator returns true if the arguments are the same value, or
;; if they are both null; and false otherwise.
;;
;; For string values, equivalence returns true if the strings are the same value
;; while ignoring case and locale, and normalizing whitespace. Normalizing
;; whitespace means that all whitespace characters are treated as equivalent,
;; with whitespace characters as defined in the whitespace lexical category.
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
;; precisions are considered a single precision using a decimal, with decimal
;; equivalence semantics.
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
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil nil))
      {:type "Null"} {:type "Null"} true))

  (testing "Boolean"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil nil))
      #elm/boolean "true" #elm/boolean "true" true
      #elm/boolean "true" #elm/boolean "false" false

      {:type "Null"} #elm/boolean "true" false
      #elm/boolean "true" {:type "Null"} false))

  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil nil))
      #elm/int "1" #elm/int "1" true
      #elm/int "1" #elm/int "2" false

      {:type "Null"} #elm/int "1" false
      #elm/int "1" {:type "Null"} false))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil nil))
      #elm/dec "1.1" #elm/dec "1.1" true
      #elm/dec "1.1" #elm/dec "2.1" false

      {:type "Null"} #elm/dec "1.1" false
      #elm/dec "1.1" {:type "Null"} false))

  (testing "Mixed Integer Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil nil))
      #elm/int "1" #elm/dec "1" true
      #elm/dec "1" #elm/int "1" true))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil nil))
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
      #elm/list [#elm/int "1"] #elm/list [#elm/int "1"] true
      #elm/list [] #elm/list [] true

      #elm/list [#elm/int "1"] #elm/list [] false
      #elm/list [#elm/int "1"] #elm/list [#elm/int "2"] false
      #elm/list [#elm/int "1" #elm/int "1"]
      #elm/list [#elm/int "1" #elm/int "2"] false

      #elm/list [#elm/int "1" {:type "Null"}]
      #elm/list [#elm/int "1" {:type "Null"}] true
      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] true
      #elm/list [#elm/date "2019"] #elm/list [#elm/date "2019-01"] false

      {:type "Null"} #elm/list [] false
      #elm/list [] {:type "Null"} false))

  (testing "Code"
    (let [ctx
          {:library
           {:codeSystems
            {:def
             [{:name "life" :id "life"}
              {:name "dktk" :id "dktk"}
              {:name "life-2010" :id "life" :version "2010"}
              {:name "life-2020" :id "life" :version "2020"}]}}}]
      (are [a b res] (= res (-eval (compile ctx (elm/equivalent [a b])) {} nil nil))
        #elm/code ["life" "0"] #elm/code ["life" "0"] true
        #elm/code ["life" "0"] #elm/code ["life" "1"] false
        #elm/code ["life" "0"] #elm/code ["dktk" "0"] false

        #elm/code ["life" "0"] #elm/code ["life-2010" "0"] true
        #elm/code ["life-2010" "0"] #elm/code ["life" "0"] true

        #elm/code ["life-2010" "0"] #elm/code ["life-2020" "0"] true
        #elm/code ["life-2020" "0"] #elm/code ["life-2010" "0"] true

        {:type "Null"} #elm/code ["life" "0"] false
        #elm/code ["life" "0"] {:type "Null"} false))))


;; 12.3. Greater
(deftest compile-greater-test
  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil nil))
      #elm/int "2" #elm/int "1" true
      #elm/int "1" #elm/int "1" false

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil nil))
      #elm/dec "2" #elm/dec "1" true
      #elm/dec "1" #elm/dec "1" false

      {:type "Null"} #elm/dec "1" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "String"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil nil))
      #elm/string "b" #elm/string "a" true
      #elm/string "a" #elm/string "a" false

      {:type "Null"} #elm/string "a" nil
      #elm/string "a" {:type "Null"} nil))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil nil))
      #elm/quantity [2] #elm/quantity [1] true
      #elm/quantity [1] #elm/quantity [1] false

      #elm/quantity [2 "s"] #elm/quantity [1 "s"] true
      #elm/quantity [2 "m"] #elm/quantity [1 "m"] true
      #elm/quantity [101 "cm"] #elm/quantity [1 "m"] true
      #elm/quantity [1 "s"] #elm/quantity [1 "s"] false
      #elm/quantity [1 "m"] #elm/quantity [1 "m"] false
      #elm/quantity [100 "cm"] #elm/quantity [1 "m"] false

      {:type "Null"} #elm/quantity [1 "s"] nil
      #elm/quantity [1 "s"] {:type "Null"} nil

      {:type "Null"} #elm/quantity [1 "s"] nil
      #elm/quantity [1 "s"] {:type "Null"} nil))

  (testing "Date with year precision"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil nil))
      #elm/date "2014" #elm/date "2013" true
      #elm/date "2013" #elm/date "2013" false

      {:type "Null"} #elm/date "2013" nil
      #elm/date "2013" {:type "Null"} nil))

  (testing "Comparing dates with mixed precisions (year and year-month) results in null."
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil nil))
      #elm/date "2013" #elm/date "2013-01" nil
      #elm/date "2013-01" #elm/date "2013" nil))

  (testing "Time"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil nil))
      #elm/time "00:00:01" #elm/time "00:00:00" true
      #elm/time "00:00:00" #elm/time "00:00:00" false

      {:type "Null"} #elm/time "00:00:00" nil
      #elm/time "00:00:00" {:type "Null"} nil)))


;; 12.4. GreaterOrEqual
(deftest compile-greater-or-equal-test
  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil nil))
      #elm/int "1" #elm/int "1" true
      #elm/int "2" #elm/int "1" true
      #elm/int "1" #elm/int "2" false

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil nil))
      #elm/dec "1.1" #elm/dec "1.1" true
      #elm/dec "2.1" #elm/dec "1.1" true
      #elm/dec "1.1" #elm/dec "2.1" false

      {:type "Null"} #elm/dec "1.1" nil
      #elm/dec "1.1" {:type "Null"} nil))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil nil))
      #elm/quantity [1] #elm/quantity [1] true
      #elm/quantity [2] #elm/quantity [1] true
      #elm/quantity [1] #elm/quantity [2] false

      #elm/quantity [1 "s"] #elm/quantity [1 "s"] true
      #elm/quantity [2 "s"] #elm/quantity [1 "s"] true
      #elm/quantity [1 "s"] #elm/quantity [2 "s"] false

      #elm/quantity [101 "cm"] #elm/quantity [1 "m"] true
      #elm/quantity [100 "cm"] #elm/quantity [1 "m"] true
      #elm/quantity [1 "m"] #elm/quantity [101 "cm"] false

      {:type "Null"} #elm/quantity [1] nil
      #elm/quantity [1] {:type "Null"} nil

      {:type "Null"} #elm/quantity [1 "s"] nil
      #elm/quantity [1 "s"] {:type "Null"} nil))

  (testing "Date"
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil nil))
      #elm/date "2013" #elm/date "2013" true
      #elm/date "2014" #elm/date "2013" true
      #elm/date "2013" #elm/date "2014" false

      #elm/date "2014-01" #elm/date "2014" nil
      #elm/date "2014" #elm/date "2014-01" nil

      {:type "Null"} #elm/date "2014" nil
      #elm/date "2014" {:type "Null"} nil))

  (testing "Time"
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil nil))
      #elm/time "00:00:00" #elm/time "00:00:00" true
      #elm/time "00:00:01" #elm/time "00:00:00" true
      #elm/time "00:00:00" #elm/time "00:00:01" false

      {:type "Null"} #elm/time "00:00:00" nil
      #elm/time "00:00:00" {:type "Null"} nil)))


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
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil nil))
      #elm/int "1" #elm/int "2" true
      #elm/int "1" #elm/int "1" false

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil nil))
      #elm/dec "1" #elm/dec "2" true
      #elm/dec "1" #elm/dec "1" false

      {:type "Null"} #elm/dec "1" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "String"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil nil))
      #elm/string "a" #elm/string "b" true
      #elm/string "a" #elm/string "a" false

      {:type "Null"} #elm/string "a" nil
      #elm/string "a" {:type "Null"} nil))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil nil))
      #elm/quantity [1] #elm/quantity [2] true
      #elm/quantity [1] #elm/quantity [1] false

      #elm/quantity [1 "s"] #elm/quantity [2 "s"] true
      #elm/quantity [1 "s"] #elm/quantity [1 "s"] false

      #elm/quantity [1 "m"] #elm/quantity [101 "cm"] true
      #elm/quantity [1 "m"] #elm/quantity [100 "cm"] false

      {:type "Null"} #elm/quantity [1] nil
      #elm/quantity [1] {:type "Null"} nil

      {:type "Null"} #elm/quantity [1 "s"] nil
      #elm/quantity [1 "s"] {:type "Null"} nil))

  (testing "Date with year precision"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil nil))
      #elm/date "2012" #elm/date "2013" true
      #elm/date "2013" #elm/date "2013" false

      {:type "Null"} #elm/date "2013" nil
      #elm/date "2013" {:type "Null"} nil))

  (testing "Comparing dates with mixed precisions (year and year-month) results in null."
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil nil))
      #elm/date "2013" #elm/date "2013-01" nil
      #elm/date "2013-01" #elm/date "2013" nil))

  (testing "Date with full precision"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {:now now} nil nil))
      #elm/date "2013-06-14" #elm/date "2013-06-15" true
      #elm/date "2013-06-15" #elm/date "2013-06-15" false

      {:type "Null"} #elm/date "2013-06-15" nil
      #elm/date "2013-06-15" {:type "Null"} nil))

  (testing "Comparing dates with mixed precisions (year-month and full) results in null."
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil nil))
      #elm/date "2013-01" #elm/date "2013-01-01" nil
      #elm/date "2013-01-01" #elm/date "2013-01" nil))

  (testing "DateTime with full precision (there is only one precision)"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {:now now} nil nil))
      #elm/date-time "2013-06-15T11" #elm/date-time "2013-06-15T12" true
      #elm/date-time "2013-06-15T12" #elm/date-time "2013-06-15T12" false))

  (testing "Time with full precision (there is only one precision)"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil nil))
      #elm/time "12:30:14" #elm/time "12:30:15" true
      #elm/time "12:30:15" #elm/time "12:30:15" false

      {:type "Null"} #elm/time "12:30:15" nil
      #elm/time "12:30:15" {:type "Null"} nil)))


;; 12.6. LessOrEqual
(deftest compile-less-or-equal-test
  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil nil))
      #elm/int "1" #elm/int "1" true
      #elm/int "1" #elm/int "2" true

      {:type "Null"} #elm/int "2" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil nil))
      #elm/dec "1" #elm/dec "2" true

      {:type "Null"} #elm/dec "2" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil nil))
      #elm/quantity [1] #elm/quantity [2] true
      #elm/quantity [1] #elm/quantity [1] true
      #elm/quantity [2] #elm/quantity [1] false

      #elm/quantity [1 "s"] #elm/quantity [2 "s"] true
      #elm/quantity [1 "s"] #elm/quantity [1 "s"] true
      #elm/quantity [2 "s"] #elm/quantity [1 "s"] false

      #elm/quantity [1 "m"] #elm/quantity [101 "cm"] true
      #elm/quantity [1 "m"] #elm/quantity [100 "cm"] true
      #elm/quantity [101 "cm"] #elm/quantity [1 "m"] false

      {:type "Null"} #elm/quantity [1 "s"] nil
      #elm/quantity [1 "s"] {:type "Null"} nil

      {:type "Null"} #elm/quantity [1] nil
      #elm/quantity [1] {:type "Null"} nil))

  (testing "Date"
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil nil))
      #elm/date "2013-06-14" #elm/date "2013-06-15" true
      #elm/date "2013-06-16" #elm/date "2013-06-15" false
      #elm/date "2013-06-15" #elm/date "2013-06-15" true

      #elm/date "2013-06-15" #elm/date-time "2013-06-15T00" nil
      #elm/date-time "2013-06-15T00" #elm/date "2013-06-15" nil))

  (testing "Time"
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil nil))
      #elm/time "00:00:00" #elm/time "00:00:00" true
      #elm/time "00:00:00" #elm/time "00:00:01" true
      #elm/time "00:00:01" #elm/time "00:00:00" false

      {:type "Null"} #elm/time "00:00:00" nil
      #elm/time "00:00:00" {:type "Null"} nil)))


;; 12.7. NotEqual
(deftest compile-not-equal-test
  (is (thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand "NotEqual")))))



;; 13. Logical Operators

;; 13.1. And
;;
;; The And operator returns the logical conjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is false, the result is false; if both arguments are true,
;; the result is true; otherwise, the result is null. Note also that ELM does
;; not prescribe short-circuit evaluation.
(deftest compile-and-test
  (are [a b res] (= res (-eval (compile {} {:type "And" :operand [a b]}) {} nil nil))
    #elm/boolean "true" #elm/boolean "true" true
    #elm/boolean "true" #elm/boolean "false" false
    #elm/boolean "true" {:type "Null"} nil

    #elm/boolean "false" #elm/boolean "true" false
    #elm/boolean "false" #elm/boolean "false" false
    #elm/boolean "false" {:type "Null"} false

    {:type "Null"} #elm/boolean "true" nil
    {:type "Null"} #elm/boolean "false" false
    {:type "Null"} {:type "Null"} nil))


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
  (are [a res] (= res (-eval (compile {} {:type "Not" :operand a}) {} nil nil))
    #elm/boolean "true" false
    #elm/boolean "false" true
    {:type "Null"} nil))


;; 13.4. Or
;;
;; The Or operator returns the logical disjunction of its arguments. Note that
;; this operator is defined using 3-valued logic semantics. This means that if
;; either argument is true, the result is true; if both arguments are false, the
;; result is false; otherwise, the result is null. Note also that ELM does not
;; prescribe short-circuit evaluation.
(deftest compile-or-test
  (are [a b res] (= res (-eval (compile {} {:type "Or" :operand [a b]}) {} nil nil))
    #elm/boolean "true" #elm/boolean "true" true
    #elm/boolean "true" #elm/boolean "false" true
    #elm/boolean "true" {:type "Null"} true

    #elm/boolean "false" #elm/boolean "true" true
    #elm/boolean "false" #elm/boolean "false" false
    #elm/boolean "false" {:type "Null"} nil

    {:type "Null"} #elm/boolean "true" true
    {:type "Null"} #elm/boolean "false" nil
    {:type "Null"} {:type "Null"} nil))


;; 13.5. Xor
;;
;; The Xor operator returns the exclusive or of its arguments. Note that this
;; operator is defined using 3-valued logic semantics. This means that the
;; result is true if and only if one argument is true and the other is false,
;; and that the result is false if and only if both arguments are true or both
;; arguments are false. If either or both arguments are null, the result is
;; null.
(deftest compile-xor-test
  (are [a b res] (= res (-eval (compile {} {:type "Xor" :operand [a b]}) {} nil nil))
    #elm/boolean "true" #elm/boolean "true" false
    #elm/boolean "true" #elm/boolean "false" true
    #elm/boolean "true" {:type "Null"} nil

    #elm/boolean "false" #elm/boolean "true" true
    #elm/boolean "false" #elm/boolean "false" false
    #elm/boolean "false" {:type "Null"} nil

    {:type "Null"} #elm/boolean "true" nil
    {:type "Null"} #elm/boolean "false" nil
    {:type "Null"} {:type "Null"} nil))



;; 14. Nullological Operators

;; 14.1. Null
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
    [{:type "Null"} #elm/int "1" #elm/int "2"] 1
    [#elm/int "2"] 2
    [#elm/list []] nil
    [{:type "Null"} #elm/list [#elm/string "a"]] ["a"]
    [#elm/list [{:type "Null"} #elm/string "a"]] "a"))


;; 14.3. IsFalse
;;
;; The IsFalse operator determines whether or not its argument evaluates to
;; false. If the argument evaluates to false, the result is true; if the
;; argument evaluates to true or null, the result is false.
(deftest compile-is-false-test
  (are [x res] (= res (-eval (compile {} {:type "IsFalse" :operand x}) {} nil nil))
    #elm/boolean "true" false
    #elm/boolean "false" true
    {:type "Null"} false))


;; 14.4. IsNull
;;
;; The IsNull operator determines whether or not its argument evaluates to null.
;; If the argument evaluates to null, the result is true; otherwise, the result
;; is false.
(deftest compile-is-null-test
  (are [x res] (= res (-eval (compile {} {:type "IsNull" :operand x}) {} nil nil))
    #elm/boolean "true" false
    #elm/boolean "false" false
    {:type "Null"} true))


;; 14.5. IsTrue
;;
;; The IsTrue operator determines whether or not its argument evaluates to true.
;; If the argument evaluates to true, the result is true; if the argument
;; evaluates to false or null, the result is false.
(deftest compile-is-true-test
  (are [x res] (= res (-eval (compile {} {:type "IsTrue" :operand x}) {} nil nil))
    #elm/boolean "true" true
    #elm/boolean "false" false
    {:type "Null"} false))



;; 15. Conditional Operators

;; 15.2. If
;;
;; The If operator evaluates a condition, and returns the then argument if the
;; condition evaluates to true; if the condition evaluates to false or null, the
;; result of the else argument is returned. The static type of the then argument
;; determines the result type of the conditional, and the else argument must be
;; of that same type.
(deftest compile-if-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
    #elm/if [#elm/boolean "true" #elm/int "1" #elm/int "2"] 1
    #elm/if [#elm/boolean "false" #elm/int "1" #elm/int "2"] 2
    #elm/if [{:type "Null"} #elm/int "1" #elm/int "2"] 2))



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
  (are [x res] (= res (-eval (compile {} {:type "Abs" :operand x}) {} nil nil))
    #elm/int "-1" 1
    #elm/int "0" 0
    #elm/int "1" 1

    #elm/dec "-1" 1M
    #elm/dec "0" 0M
    #elm/dec "1" 1M

    #elm/quantity [-1] 1
    #elm/quantity [0] 0
    #elm/quantity [1] 1

    #elm/quantity [-1M] 1M
    #elm/quantity [0M] 0M
    #elm/quantity [1M] 1M

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
    (are [x y res] (= res (-eval (compile {} (elm/add [x y])) {} nil nil))
      #elm/int "-1" #elm/int "-1" -2
      #elm/int "-1" #elm/int "0" -1
      #elm/int "-1" #elm/int "1" 0
      #elm/int "1" #elm/int "0" 1
      #elm/int "1" #elm/int "1" 2

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Adding zero integer to any integer or decimal doesn't change it"
    (satisfies-prop 100
                    (prop/for-all [operand (s/gen (s/or :i :elm/integer :d :elm/decimal))]
                      (let [elm (elm/equal [(elm/add [operand #elm/int "0"]) operand])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding zero decimal to any decimal doesn't change it"
    (satisfies-prop 100
                    (prop/for-all [operand (s/gen :elm/decimal)]
                      (let [elm (elm/equal [(elm/add [operand #elm/dec "0"]) operand])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding identical integers equals multiplying the same integer by two"
    (satisfies-prop 100
                    (prop/for-all [integer (s/gen :elm/integer)]
                      (let [elm (elm/equivalent [(elm/add [integer integer])
                                                 (elm/multiply [integer #elm/int "2"])])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Decimal"
    (testing "Decimal"
      (are [x y res] (= res (-eval (compile {} (elm/add [x y])) {} nil nil))
        #elm/dec "-1.1" #elm/dec "-1.1" -2.2M
        #elm/dec "-1.1" #elm/dec "0" -1.1M
        #elm/dec "-1.1" #elm/dec "1.1" 0M
        #elm/dec "1.1" #elm/dec "0" 1.1M
        #elm/dec "1.1" #elm/dec "1.1" 2.2M

        {:type "Null"} #elm/dec "1" nil
        #elm/dec "1" {:type "Null"} nil))

    (testing "Mix with integer"
      (are [x y res] (= res (-eval (compile {} (elm/add [x y])) {} nil nil))
        #elm/dec "1" #elm/int "1" 2M))

    (testing "Trailing zeros are preserved"
      (are [x y res] (= res (str (-eval (compile {} (elm/add [x y])) {} nil nil)))
        #elm/dec "1.23" #elm/dec "1.27" "2.50"))

    (testing "Arithmetic overflow results in nil"
      (are [x y] (nil? (-eval (compile {} (elm/add [x y])) {} nil nil))
        #elm/dec "99999999999999999999" #elm/dec "1"
        #elm/dec "99999999999999999999.99999999" #elm/dec "1")))

  (testing "Adding identical decimals equals multiplying the same decimal by two"
    (satisfies-prop 100
                    (prop/for-all [decimal (s/gen :elm/decimal)]
                      (let [elm (elm/equal [(elm/add [decimal decimal])
                                            (elm/multiply [decimal #elm/int "2"])])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding identical decimals and dividing by two results in the same decimal"
    (satisfies-prop 100
                    (prop/for-all [decimal (s/gen :elm/decimal)]
                      (let [elm (elm/equal [(elm/divide [(elm/add [decimal decimal])
                                                         #elm/int "2"])
                                            decimal])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Time-based quantity"
    (are [x y res] (= res (-eval (compile {} (elm/add [x y])) {} nil nil))
      #elm/quantity [1 "year"] #elm/quantity [1 "year"] (period 2 0 0)
      #elm/quantity [1 "year"] #elm/quantity [1 "month"] (period 1 1 0)
      #elm/quantity [1 "year"] #elm/quantity [1 "day"] (period 1 0 (* 24 3600 1000))

      #elm/quantity [1 "day"] #elm/quantity [1 "day"] (period 0 0 (* 2 24 3600 1000))
      #elm/quantity [1 "day"] #elm/quantity [1 "hour"] (period 0 0 (* 25 3600 1000))

      #elm/quantity [1 "year"] #elm/quantity [1.1M "year"] (period 2.1M 0 0)
      #elm/quantity [1 "year"] #elm/quantity [13.1M "month"] (period 2 1.1M 0)))

  (testing "UCUM quantity"
    (are [x y res] (= res (-eval (compile {} (elm/add [x y])) {} nil nil))
      #elm/quantity [1 "m"] #elm/quantity [1 "m"] (quantity 2 "m")
      #elm/quantity [1 "m"] #elm/quantity [1 "cm"] (quantity 1.01M "m")))

  (testing "Incompatible UCUM Quantity Subtractions"
    (are [a b] (thrown? UnconvertibleException (-eval (compile {} (elm/add [a b])) {} nil nil))
      #elm/quantity [1 "cm2"] #elm/quantity [1 "cm"]
      #elm/quantity [1 "m"] #elm/quantity [1 "s"]))

  (testing "Adding identical quantities equals multiplying the same quantity with two"
    (satisfies-prop 100
                    (prop/for-all [quantity (s/gen :elm/quantity)]
                      (let [elm (elm/equal [(elm/add [quantity quantity])
                                            (elm/multiply [quantity #elm/int "2"])])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Adding identical quantities and dividing by two results in the same quantity"
    (satisfies-prop 100
                    (prop/for-all [quantity (s/gen :elm/quantity)]
                      (let [elm (elm/equal [(elm/divide [(elm/add [quantity quantity])
                                                         #elm/int "2"])
                                            quantity])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Date + Quantity"
    (are [x y res] (= res (-eval (compile {} (elm/add [x y])) {} nil nil))
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
    (are [x y res] (= res (-eval (compile {} (elm/add [x y])) {} nil nil))
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "year"] (LocalDateTime/of 2020 1 1 0 0 0)
      #elm/date-time "2012-02-29T00" #elm/quantity [1 "year"] (LocalDateTime/of 2013 2 28 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "month"] (LocalDateTime/of 2019 2 1 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "day"] (LocalDateTime/of 2019 1 2 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "hour"] (LocalDateTime/of 2019 1 1 1 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "minute"] (LocalDateTime/of 2019 1 1 0 1 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "second"] (LocalDateTime/of 2019 1 1 0 0 1)))

  (testing "Time + Quantity"
    (are [x y res] (= res (-eval (compile {} (elm/add [x y])) {} nil nil))
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
  (are [x res] (= res (-eval (compile {} {:type "Ceiling" :operand x}) {} nil nil))
    #elm/int "1" 1

    #elm/dec "1.1" 2

    {:type "Null"} nil))


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
    (are [a b res] (= res (some-> (-eval (compile {} (elm/divide [a b])) {} nil nil) str))
      #elm/dec "1" #elm/dec "2" "0.5"
      #elm/dec "1.1" #elm/dec "2" "0.55"
      #elm/dec "10" #elm/dec "3" "3.33333333"

      #elm/dec "3" #elm/int "2" "1.5"

      #elm/dec "1" #elm/dec "0" nil
      ; test zero with different precision
      #elm/dec "1" #elm/dec "0.0" nil

      #elm/dec "1.1" {:type "Null"} nil
      {:type "Null"} #elm/dec "1.1" nil))

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
    (are [a b res] (= res (-eval (compile {} (elm/divide [a b])) {} nil nil))
      #elm/quantity [1M "m"] #elm/int "2" (quantity 0.5M "m")

      #elm/quantity [1 "m"] #elm/quantity [1 "s"] (quantity 1 "m/s")
      #elm/quantity [1M "m"] #elm/quantity [1M "s"] (quantity 1M "m/s")

      #elm/quantity [12 "cm2"] #elm/quantity [3 "cm"] (quantity 4 "cm")

      #elm/quantity [1 "m"] {:type "Null"} nil
      {:type "Null"} #elm/quantity [1 "m"] nil)))


;; 16.5. Exp
;;
;; The Exp operator returns e raised to the given power.
;;
;; If the argument is null, the result is null.
(deftest compile-exp-test
  (are [x res] (= res (-eval (compile {} {:type "Exp" :operand x}) {} nil nil))
    #elm/int "0" 1M
    #elm/dec "0" 1M
    {:type "Null"} nil))


;; 16.6. Floor
;;
;; The Floor operator returns the first integer less than or equal to the
;; argument.
;;
;; If the argument is null, the result is null.
(deftest compile-floor-test
  (are [x res] (= res (-eval (compile {} {:type "Floor" :operand x}) {} nil nil))
    #elm/int "1" 1
    #elm/dec "1.1" 1
    {:type "Null"} nil))


;; 16.7. Log
;;
;; The Log operator computes the logarithm of its first argument, using the
;; second argument as the base.
;;
;; If either argument is null, the result is null.
(deftest compile-log-test
  (are [x base res] (= res (-eval (compile {} {:type "Log" :operand [x base]}) {} nil nil))
    #elm/int "16" #elm/int "2" 4M

    #elm/dec "100" #elm/dec "10" 2M
    #elm/dec "1" #elm/dec "1" nil

    #elm/int "0" #elm/int "2" nil
    #elm/dec "0" #elm/int "2" nil

    {:type "Null"} #elm/int "1" nil
    #elm/int "1" {:type "Null"} nil

    {:type "Null"} #elm/dec "1" nil
    #elm/dec "1" {:type "Null"} nil))


;; 16.8. Ln
;;
;; The Ln operator computes the natural logarithm of its argument.
;;
;; If the argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, the result is null.
(deftest compile-ln-test
  (are [x res] (= res (-eval (compile {} {:type "Ln" :operand x}) {} nil nil))
    #elm/int "1" 0M
    #elm/dec "1" 0M

    #elm/int "0" nil
    #elm/dec "0" nil

    #elm/int "-1" nil
    #elm/dec "-1" nil

    {:type "Null"} nil))


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
  (are [x div res] (= res (-eval (compile {} {:type "Modulo" :operand [x div]}) {} nil nil))
    #elm/int "1" #elm/int "2" 1
    #elm/int "3" #elm/int "2" 1
    #elm/int "5" #elm/int "3" 2

    #elm/dec "1" #elm/dec "2" 1M
    #elm/dec "3" #elm/dec "2" 1M
    #elm/dec "5" #elm/dec "3" 2M

    #elm/dec "2.5" #elm/dec "2" 0.5M

    #elm/int "1" #elm/int "0" nil
    #elm/dec "1" #elm/dec "0" nil

    {:type "Null"} #elm/int "1" nil
    #elm/int "1" {:type "Null"} nil

    {:type "Null"} #elm/dec "1.1" nil
    #elm/dec "1.1" {:type "Null"} nil))


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
    (are [x y res] (= res (-eval (compile {} (elm/multiply [x y])) {} nil nil))
      #elm/int "1" #elm/int "2" 2

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (testing "Decimal"
      (are [x y res] (= res (-eval (compile {} (elm/multiply [x y])) {} nil nil))
        #elm/dec "1" #elm/dec "2" 2M
        #elm/dec "1.23456" #elm/dec "1.23456" 1.52413839M

        {:type "Null"} #elm/dec "1" nil
        #elm/dec "1" {:type "Null"} nil))

    (testing "Arithmetic overflow results in nil"
      (are [x y] (nil? (-eval (compile {} (elm/multiply [x y])) {} nil nil))
        #elm/dec "99999999999999999999" #elm/dec "2"
        #elm/dec "99999999999999999999.99999999" #elm/dec "2")))

  (testing "UCUM Quantity"
    (are [x y res] (= res (-eval (compile {} (elm/multiply [x y])) {} nil nil))
      #elm/quantity [1 "m"] #elm/int "2" (quantity 2 "m")
      #elm/quantity [1 "m"] #elm/quantity [2 "m"] (quantity 2 "m2")

      {:type "Null"} #elm/quantity [1 "m"] nil
      #elm/quantity [1 "m"] {:type "Null"} nil)))


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
  (are [x res] (= res (-eval (compile {} {:type "Negate" :operand x}) {} nil nil))
    #elm/int "1" -1

    #elm/dec "1" -1M

    #elm/quantity [1] -1
    #elm/quantity [1M] -1M
    #elm/quantity [1 "m"] (quantity -1 "m")
    #elm/quantity [1M "m"] (quantity -1M "m")

    {:type "Null"} nil))


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
    (are [a b res] (= res (-eval (compile {} (elm/power [a b])) {} nil nil))
      #elm/int "10" #elm/int "2" 100
      #elm/int "2" #elm/int "-2" 0.25M

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/power [a b])) {} nil nil))
      #elm/dec "2.5" #elm/dec "2" 6.25M
      #elm/dec "10" #elm/dec "2" 100M
      #elm/dec "4" #elm/dec "0.5" 2M

      {:type "Null"} #elm/dec "1" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "Mixed"
    (are [a b res] (= res (-eval (compile {} (elm/power [a b])) {} nil nil))
      #elm/dec "2.5" #elm/int "2" 6.25M
      #elm/dec "10" #elm/int "2" 100M
      #elm/dec "10" #elm/int "2" 100M)))


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
  (are [x res] (= res (-eval (compile {} (elm/predecessor x)) {} nil nil))
    #elm/int "0" -1
    #elm/dec "0" -1E-8M
    #elm/date "2019" (Year/of 2018)
    #elm/date "2019-01" (YearMonth/of 2018 12)
    #elm/date "2019-01-01" (LocalDate/of 2018 12 31)
    #elm/date-time "2019-01-01T00" (LocalDateTime/of 2018 12 31 23 59 59 999000000)
    #elm/time "12:00" (local-time 11 59)
    #elm/quantity [0 "m"] (quantity -1 "m")
    #elm/quantity [0M "m"] (quantity -1E-8M "m")
    {:type "Null"} nil)

  (are [x] (thrown? Exception (-eval (compile {} (elm/predecessor x)) {} nil nil))
    (elm/dec (str decimal/min))
    #elm/date "0001"
    #elm/date "0001-01"
    #elm/date "0001-01-01"
    #elm/time "00:00:00.0"
    #elm/date-time "0001-01-01T00:00:00.0"))


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
  (testing "Without precision"
    (are [x res] (= res (-eval (compile {} (elm/round [x])) {} nil nil))
      #elm/int "1" 1M
      #elm/dec "1" 1M
      #elm/dec "0.5" 1M
      #elm/dec "0.4" 0M
      #elm/dec "-0.4" 0M
      #elm/dec "-0.5" -1M
      #elm/dec "-0.6" -1M
      #elm/dec "-1.1" -1M
      #elm/dec "-1.5" -2M
      #elm/dec "-1.6" -2M
      {:type "Null"} nil))

  (testing "With literal precision"
    (are [x precision res] (= res (-eval (compile {} (elm/round [x precision])) {} nil nil))
      #elm/dec "3.14159" #elm/int "3" 3.142M))

  (testing "With non-literal precision"
    (are [x precision res] (= res (-eval (compile {} (elm/round [x precision])) {} nil nil))
      #elm/dec "3.14159" #elm/add [#elm/int "2" #elm/int "1"] 3.142M)))


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
      #elm/int "-1" #elm/int "-1" 0
      #elm/int "-1" #elm/int "0" -1
      #elm/int "1" #elm/int "1" 0
      #elm/int "1" #elm/int "0" 1
      #elm/int "1" #elm/int "-1" 2

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Subtracting identical integers results in zero"
    (satisfies-prop 100
                    (prop/for-all [integer (s/gen :elm/integer)]
                      (zero? (-eval (compile {} (elm/subtract [integer integer])) {} nil nil)))))

  (testing "Decimal"
    (testing "Decimal"
      (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
        #elm/dec "-1" #elm/dec "-1" 0M
        #elm/dec "-1" #elm/dec "0" -1M
        #elm/dec "1" #elm/dec "1" 0M
        #elm/dec "1" #elm/dec "0" 1M
        #elm/dec "1" #elm/dec "-1" 2M

        {:type "Null"} #elm/dec "1.1" nil
        #elm/dec "1.1" {:type "Null"} nil))

    (testing "Mix with integer"
      (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
        #elm/dec "1" #elm/int "1" 0M))

    (testing "Arithmetic overflow results in nil"
      (are [x y] (nil? (-eval (compile {} (elm/subtract [x y])) {} nil nil))
        #elm/dec "-99999999999999999999" #elm/dec "1"
        #elm/dec "-99999999999999999999.99999999" #elm/dec "1")))

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
    (satisfies-prop 100
                    (prop/for-all [quantity (s/gen :elm/quantity)]
                      ;; Can't test for zero because can't extract value from quantity
                      ;; so use negate trick
                      (let [elm (elm/equal [(elm/negate (elm/subtract [quantity quantity]))
                                            (elm/subtract [quantity quantity])])]
                        (true? (-eval (compile {} elm) {} nil nil))))))

  (testing "Date - Quantity"
    (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
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
    (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "year"] (LocalDateTime/of 2018 1 1 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "month"] (LocalDateTime/of 2018 12 1 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "day"] (LocalDateTime/of 2018 12 31 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "hour"] (LocalDateTime/of 2018 12 31 23 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "minute"] (LocalDateTime/of 2018 12 31 23 59 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "second"] (LocalDateTime/of 2018 12 31 23 59 59)))

  (testing "Time - Quantity"
    (are [x y res] (= res (-eval (compile {} (elm/subtract [x y])) {} nil nil))
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
    #elm/int "0" 1
    #elm/dec "0" 1E-8M
    #elm/date "2019" (Year/of 2020)
    #elm/date "2019-01" (YearMonth/of 2019 2)
    #elm/date "2019-01-01" (LocalDate/of 2019 1 2)
    #elm/date-time "2019-01-01T00" (LocalDateTime/of 2019 1 1 0 0 0 1000000)
    #elm/time "00:00:00" (local-time 0 0 1)
    #elm/quantity [0 "m"] (quantity 1 "m")
    #elm/quantity [0M "m"] (quantity 1E-8M "m")
    {:type "Null"} nil)

  (are [x] (thrown? Exception (-eval (compile {} (elm/successor x)) {} nil nil))
    (elm/dec (str decimal/max))
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
    #elm/int "1" 1
    #elm/dec "1.1" 1
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
    #elm/int "1" #elm/int "2" 0
    #elm/int "2" #elm/int "2" 1

    #elm/dec "4.14" #elm/dec "2.06" 2M

    #elm/int "1" #elm/int "0" nil

    {:type "Null"} #elm/int "1" nil
    #elm/int "1" {:type "Null"} nil))


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
      #elm/string "a" #elm/int "0" "a"
      #elm/string "ab" #elm/int "1" "b"

      #elm/string "" #elm/int "-1" nil
      #elm/string "" #elm/int "0" nil
      #elm/string "a" #elm/int "1" nil

      #elm/string "" {:type "Null"} nil
      {:type "Null"} #elm/int "0" nil))

  (testing "List"
    (are [x i res] (= res (-eval (compile {} {:type "Indexer" :operand [x i]}) {} nil nil))
      #elm/list [#elm/int "1"] #elm/int "0" 1
      #elm/list [#elm/int "1" #elm/int "2"] #elm/int "1" 2

      #elm/list [] #elm/int "-1" nil
      #elm/list [] #elm/int "0" nil
      #elm/list [#elm/int "1"] #elm/int "1" nil

      #elm/list [] {:type "Null"} nil
      {:type "Null"} #elm/int "0" nil)))


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
    #elm/list [#elm/int "1"] 1

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
      #elm/string "ab" #elm/int "1" "b"

      #elm/string "a" #elm/int "-1" nil
      #elm/string "a" #elm/int "1" nil
      {:type "Null"} #elm/int "0" nil
      #elm/string "a" {:type "Null"} nil
      {:type "Null"} {:type "Null"} nil))

  (testing "With length"
    (are [s start-index length res] (= res (-eval (compile {} {:type "Substring" :stringToSub s :startIndex start-index :length length}) {} nil nil))
      #elm/string "a" #elm/int "0" #elm/int "1" "a"
      #elm/string "a" #elm/int "0" #elm/int "2" "a"
      #elm/string "abc" #elm/int "1" #elm/int "1" "b"

      #elm/string "a" #elm/int "-1" #elm/int "0" nil
      #elm/string "a" #elm/int "2" #elm/int "0" nil
      {:type "Null"} #elm/int "0" #elm/int "0" nil
      #elm/string "a" {:type "Null"} #elm/int "0" nil
      {:type "Null"} {:type "Null"} #elm/int "0" nil)))


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

;; 18.4. Equal
(deftest compile-equal-date-time-test
  (testing "date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/equal [#elm/date "2012" #elm/date "2012"] true
      #elm/equal [#elm/date "2012" #elm/date "2013"] false
      #elm/equal [{:type "Null"} #elm/date "2012"] nil
      #elm/equal [#elm/date "2012" {:type "Null"}] nil)))


;; 18.6. Date
;;
;; The Date operator constructs a date value from the given components.
;;
;; At least one component must be specified, and no component may be specified
;; at a precision below an unspecified precision. For example, month may be null,
;; but if it is, day must be null as well.
(deftest compile-date-test
  (testing "literal year"
    (are [elm res] (= res (compile {} elm))
      #elm/date "2019"
      (Year/of 2019)))

  (testing "non-literal year"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/date [#elm/add [#elm/int "2018" #elm/int "1"]]
      (Year/of 2019)))

  (testing "literal year-month"
    (are [elm res] (= res (compile {} elm))
      #elm/date "2019-03"
      (YearMonth/of 2019 3)))

  (testing "non-literal year-month"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/date [#elm/int "2019" #elm/add [#elm/int "2" #elm/int "1"]]
      (YearMonth/of 2019 3)))

  (testing "literal date"
    (are [elm res] (= res (compile {} elm))
      #elm/date "2019-03-23"
      (LocalDate/of 2019 3 23)))

  (testing "non-literal date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/date [#elm/int "2019" #elm/int "3"
                 #elm/add [#elm/int "22" #elm/int "1"]]
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
                      (instance? Temporal (compile {} date))))))


;; 18.7. DateFrom
;;
;; The DateFrom operator returns the date (with no time components specified) of
;; the argument.
;;
;; If the argument is null, the result is null.
(deftest compile-date-from-test
  (are [x res] (= res (-eval (compile {} {:type "DateFrom" :operand x}) {:now now} nil nil))
    #elm/date "2019-04-17" (LocalDate/of 2019 4 17)
    #elm/date-time "2019-04-17T12:48" (LocalDate/of 2019 4 17)
    {:type "Null"} nil))


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
  (testing "literal year"
    (are [elm res] (= res (compile {} elm))
      #elm/date-time "2019" (Year/of 2019)))

  (testing "null year"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/date-time [#elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]] nil))

  (testing "non-literal year"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/date-time [#elm/add [#elm/int "2018" #elm/int "1"]]
      (Year/of 2019)))

  (testing "literal year-month"
    (are [elm res] (= res (compile {} elm))
      #elm/date-time "2019-03"
      (YearMonth/of 2019 3)))

  (testing "non-literal year-month"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/date-time [#elm/int "2019" #elm/add [#elm/int "2" #elm/int "1"]]
      (YearMonth/of 2019 3)))

  (testing "literal date"
    (are [elm res] (= res (compile {} elm))
      #elm/date-time "2019-03-23"
      (LocalDate/of 2019 3 23)))

  (testing "non-literal date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/date-time [#elm/int "2019" #elm/int "3"
                      #elm/add [#elm/int "22" #elm/int "1"]]
      (LocalDate/of 2019 3 23)))

  (testing "literal hour"
    (are [elm res] (= res (compile {} elm))
      #elm/date-time "2019-03-23T12"
      (LocalDateTime/of 2019 3 23 12 0 0)))

  (testing "non-literal hour"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/date-time [#elm/int "2019" #elm/int "3" #elm/int "23"
                      #elm/add [#elm/int "11" #elm/int "1"]]
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
      #elm/date-time [#elm/int "2019" #elm/int "3" #elm/int "23"
                      #elm/int "12" #elm/int "13" #elm/int "14" #elm/int "0"
                      #elm/dec "-2"]
      (LocalDateTime/of 2019 3 23 14 13 14)

      #elm/date-time [#elm/int "2019" #elm/int "3" #elm/int "23"
                      #elm/int "12" #elm/int "13" #elm/int "14" #elm/int "0"
                      #elm/dec "-1"]
      (LocalDateTime/of 2019 3 23 13 13 14)

      #elm/date-time [#elm/int "2019" #elm/int "3" #elm/int "23"
                      #elm/int "12" #elm/int "13" #elm/int "14" #elm/int "0"
                      #elm/dec "0"]
      (LocalDateTime/of 2019 3 23 12 13 14)

      #elm/date-time [#elm/int "2019" #elm/int "3" #elm/int "23"
                      #elm/int "12" #elm/int "13" #elm/int "14" #elm/int "0"
                      #elm/dec "1"]
      (LocalDateTime/of 2019 3 23 11 13 14)

      #elm/date-time [#elm/int "2019" #elm/int "3" #elm/int "23"
                      #elm/int "12" #elm/int "13" #elm/int "14" #elm/int "0"
                      #elm/dec "2"]
      (LocalDateTime/of 2019 3 23 10 13 14)

      #elm/date-time [#elm/int "2012" #elm/int "3" #elm/int "10"
                      #elm/int "10" #elm/int "20" #elm/int "0" #elm/int "999"
                      #elm/dec "7"]
      (LocalDateTime/of 2012 3 10 3 20 0 999000000)))

  (testing "with decimal offset"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil nil))
      #elm/date-time [#elm/int "2019" #elm/int "3" #elm/int "23"
                      #elm/int "12" #elm/int "13" #elm/int "14" #elm/int "0"
                      #elm/dec "1.5"]
      (LocalDateTime/of 2019 3 23 10 43 14)))

  (testing "an ELM date-time (only literals) always evaluates to something implementing Temporal"
    (satisfies-prop 100
                    (prop/for-all [date-time (s/gen :elm/literal-date-time)]
                      (instance? Temporal (-eval (compile {} date-time) {:now now} nil nil))))))


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
    #elm/date "2019-04-17" #elm/date "2019-04-18" false
    )

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
      #elm/interval [#elm/int "1" #elm/int "2"]
      #elm/interval [#elm/int "2" #elm/int "3"] true)))


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
      #elm/interval [#elm/int "2" #elm/int "3"]
      #elm/interval [#elm/int "1" #elm/int "2"] true)))


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
  (testing "literal hour"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/int "12"]
      (local-time 12)))

  (testing "non-literal hour"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/time [#elm/add [#elm/int "11" #elm/int "1"]]
      (local-time 12)))

  (testing "literal hour-minute"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/int "12" #elm/int "13"]
      (local-time 12 13)))

  (testing "non-literal hour-minute"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/time [#elm/int "12" #elm/add [#elm/int "12"
                                         #elm/int "1"]]
      (local-time 12 13)))

  (testing "literal hour-minute-second"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/int "12" #elm/int "13" #elm/int "14"]
      (local-time 12 13 14)))

  (testing "non-literal hour-minute-second"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/time [#elm/int "12" #elm/int "13"
                 #elm/add [#elm/int "13" #elm/int "1"]]
      (local-time 12 13 14)))

  (testing "literal hour-minute-second-millisecond"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/int "12" #elm/int "13" #elm/int "14"
                 #elm/int "15"]
      (local-time 12 13 14 15)))

  (testing "non-literal hour-minute-second-millisecond"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/time [#elm/int "12" #elm/int "13" #elm/int "14"
                 #elm/add [#elm/int "14" #elm/int "1"]]
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

(def interval-zero #elm/interval [#elm/int "0" #elm/int "0"])

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
  (testing "Literal interval"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/interval [#elm/int "1" #elm/int "2"] (interval 1 2)
      #elm/interval [#elm/dec "1" #elm/dec "2"] (interval 1M 2M)

      #elm/interval [:< #elm/int "1" #elm/int "2"] (interval 2 2)
      #elm/interval [#elm/int "1" #elm/int "2" :>] (interval 1 1)
      #elm/interval [:< #elm/int "1" #elm/int "3" :>] (interval 2 2)))

  (testing "Invalid interval"
    (are [elm] (thrown? Exception (-eval (compile {} elm) {} nil nil))
      #elm/interval [#elm/int "5" #elm/int "3"])))


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
        #elm/interval [#elm/int "3" #elm/int "4"]
        #elm/interval [#elm/int "1" #elm/int "2"] true
        #elm/interval [#elm/int "2" #elm/int "3"]
        #elm/interval [#elm/int "1" #elm/int "2"] false))

    (testing "if one of the intervals is open, start and end can be the same (2)"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        #elm/interval [#elm/int "2" #elm/int "3"]
        #elm/interval [#elm/int "1" #elm/int "2" :>] true
        #elm/interval [:< #elm/int "2" #elm/int "3"]
        #elm/interval [#elm/int "1" #elm/int "2"] true
        #elm/interval [:< #elm/int "2" #elm/int "3"]
        #elm/interval [#elm/int "1" #elm/int "2" :>] true))

    (testing "if both intervals are open, start and end can overlap slightly"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        #elm/interval [:< #elm/int "2" #elm/int "4"]
        #elm/interval [#elm/int "1" #elm/int "3" :>] true))

    (testing "if one of the relevant bounds is infinity, the result is false"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/int "3"]
        #elm/interval [#elm/int "1" #elm/int "2"] false
        #elm/interval [#elm/int "2" #elm/int "3"]
        #elm/interval [#elm/int "1" {:type "Null"}] false))

    (testing "if the second interval has an unknown high bound, the result is null"
      (are [a b res] (= res (-eval (compile {} (elm/after [a b])) {} nil nil))
        #elm/interval [#elm/int "2" #elm/int "3"]
        #elm/interval [#elm/int "1" {:type "Null"} :>] nil))))


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
        #elm/interval [#elm/int "1" #elm/int "2"]
        #elm/interval [#elm/int "3" #elm/int "4"] true
        #elm/interval [#elm/int "1" #elm/int "2"]
        #elm/interval [#elm/int "2" #elm/int "3"] false))

    (testing "if one of the intervals is open, start and end can be the same (2)"
      (are [a b res] (= res (-eval (compile {} (elm/before [a b])) {} nil nil))
        #elm/interval [#elm/int "1" #elm/int "2" :>]
        #elm/interval [#elm/int "2" #elm/int "3"] true
        #elm/interval [#elm/int "1" #elm/int "2"]
        #elm/interval [:< #elm/int "2" #elm/int "3"] true
        #elm/interval [#elm/int "1" #elm/int "2" :>]
        #elm/interval [:< #elm/int "2" #elm/int "3"] true))

    (testing "if both intervals are open, start and end can overlap slightly"
      (are [a b res] (= res (-eval (compile {} (elm/before [a b])) {} nil nil))
        #elm/interval [#elm/int "1" #elm/int "3" :>]
        #elm/interval [:< #elm/int "2" #elm/int "4"] true))

    (testing "if one of the relevant bounds is infinity, the result is false"
      (are [a b res] (= res (-eval (compile {} (elm/before [a b])) {} nil nil))
        #elm/interval [#elm/int "1" {:type "Null"}]
        #elm/interval [#elm/int "2" #elm/int "3"] false
        #elm/interval [#elm/int "1" #elm/int "2"]
        #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/int "3"] false))

    (testing "if the second interval has an unknown low bound, the result is null"
      (are [a b res] (= res (-eval (compile {} (elm/before [a b])) {} nil nil))
        #elm/interval [#elm/int "1" #elm/int "2"]
        #elm/interval [:< {:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/int "3"] nil))))


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
    #elm/list [#elm/interval [#elm/int "1" #elm/int "2"]]
    {:type "Null"}
    [(interval 1 2)]

    #elm/list [#elm/interval [#elm/int "1" #elm/int "2"]
               #elm/interval [#elm/int "2" #elm/int "3"]]
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
        #elm/interval [#elm/int "1" #elm/int "1"] #elm/int "1" true
        #elm/interval [#elm/int "1" #elm/int "1"] #elm/int "2" false)))

  (testing "List"
    (are [list x res] (= res (-eval (compile {} (elm/contains [list x])) {} nil nil))
      #elm/list [] #elm/int "1" false

      #elm/list [#elm/int "1"] #elm/int "1" true
      #elm/list [#elm/int "1"] #elm/int "2" false

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
      #elm/interval [#elm/int "1" #elm/int "2"] 2
      #elm/interval [#elm/int "1" #elm/int "2" :>] 1
      #elm/interval [#elm/int "1" {:type "Null"}] Integer/MAX_VALUE))

  (testing "Decimal"
    (are [x res] (= res (-eval (compile {} {:type "End" :operand x}) {} nil nil))
      #elm/interval [#elm/dec "1" #elm/dec "2.1"] 2.1M
      #elm/interval [#elm/dec "1" #elm/dec "2.1" :>] 2.09999999M
      #elm/interval [#elm/dec "1" {:type "Null"}] decimal/max)))


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
      #elm/interval [#elm/int "1" #elm/int "3"]
      #elm/interval [#elm/int "1" #elm/int "3"] true
      #elm/interval [#elm/int "2" #elm/int "3"]
      #elm/interval [#elm/int "1" #elm/int "3"] true
      #elm/interval [#elm/int "1" #elm/int "3"]
      #elm/interval [#elm/int "2" #elm/int "3"] false)))


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
      #elm/list [] #elm/list [#elm/int "1"] []
      #elm/list [#elm/int "1"] #elm/list [#elm/int "1"] []
      #elm/list [#elm/int "1"] #elm/list [] [1]
      #elm/list [#elm/int "1"] #elm/list [#elm/int "2"] [1]
      #elm/list [#elm/int "1" #elm/int "2"] #elm/list [#elm/int "2"] [1]
      #elm/list [#elm/int "1" #elm/int "2"] #elm/list [#elm/int "1"] [2]

      #elm/list [] {:type "Null"} nil))

  (testing "Interval"
    (testing "Null"
      (are [a b res] (= res (-eval (compile {} (elm/except [a b])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [a b res] (= res (-eval (compile {} (elm/except [a b])) {} nil nil))
        #elm/interval [#elm/int "1" #elm/int "3"]
        #elm/interval [#elm/int "3" #elm/int "4"]
        (interval 1 2)

        #elm/interval [#elm/int "3" #elm/int "5"]
        #elm/interval [#elm/int "1" #elm/int "3"]
        (interval 4 5)))))


;; 19.12. In
;;
;; Normalized to Contains
(deftest compile-in-test
  (is (thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand "In")))))


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
      #elm/list [#elm/int "1"] #elm/list [#elm/int "1"] true
      #elm/list [#elm/int "1" #elm/int "2"] #elm/list [#elm/int "1"] true

      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] false

      #elm/list [] {:type "Null"} nil))

  (testing "Interval"
    (testing "Null"
      (are [a b res] (= res (-eval (compile {} (elm/includes [a b])) {} nil nil))
        interval-zero {:type "Null"} nil))

    (testing "Integer"
      (are [a b res] (= res (-eval (compile {} (elm/includes [a b])) {} nil nil))
        #elm/interval [#elm/int "1" #elm/int "2"]
        #elm/interval [#elm/int "1" #elm/int "2"] true
        #elm/interval [#elm/int "1" #elm/int "2"]
        #elm/interval [#elm/int "1" #elm/int "3"] false))))


;; 19.14. IncludedIn
;;
;; Normalized to Includes
(deftest compile-included-in-test
  (is (thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand "IncludedIn")))))


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
    (are [a b res] (= res (-eval (compile {} (elm/intersect [a b])) {} nil nil))
      #elm/list [{:type "Null"}] #elm/list [{:type "Null"}] []
      #elm/list [#elm/int "1"] #elm/list [#elm/int "1"] [1]
      #elm/list [#elm/int "1"] #elm/list [#elm/int "2"] []
      #elm/list [#elm/int "1"] #elm/list [#elm/int "1" #elm/int "2"] [1]

      #elm/list [] {:type "Null"} nil))

  (testing "Interval"
    (are [a b res] (= res (-eval (compile {} (elm/intersect [a b])) {} nil nil))
      #elm/interval [#elm/int "1" #elm/int "2"]
      #elm/interval [#elm/int "2" #elm/int "3"]
      (interval 2 2)

      #elm/interval [#elm/int "2" #elm/int "3"]
      #elm/interval [#elm/int "1" #elm/int "2"]
      (interval 2 2)

      #elm/interval [#elm/int "1" #elm/int "10"]
      #elm/interval [#elm/int "5" #elm/int "8"]
      (interval 5 8)

      #elm/interval [#elm/int "1" #elm/int "10"]
      #elm/interval [#elm/int "5" {:type "Null"} :>]
      nil

      #elm/interval [#elm/int "1" #elm/int "2"]
      #elm/interval [#elm/int "3" #elm/int "4"]
      nil

      interval-zero {:type "Null"} nil))

  (testing "Null"
    (are [a b res] (= res (-eval (compile {} (elm/intersect [a b])) {} nil nil))
      {:type "Null"} {:type "Null"} nil)))


;; 19.16. Meets
;;
;; Normalized to MeetsBefore or MeetsAfter
(deftest compile-meets-test
  (is (thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand "Meets")))))


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
      #elm/interval [#elm/int "1" #elm/int "2"]
      #elm/interval [#elm/int "3" #elm/int "4"] true
      #elm/interval [#elm/int "1" #elm/int "2"]
      #elm/interval [#elm/int "4" #elm/int "5"] false)))


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
      #elm/interval [#elm/int "3" #elm/int "4"]
      #elm/interval [#elm/int "1" #elm/int "2"] true
      #elm/interval [#elm/int "4" #elm/int "5"]
      #elm/interval [#elm/int "1" #elm/int "2"] false)))


;; 19.20. Overlaps
;;
;; Normalized to OverlapsBefore or OverlapsAfter
(deftest compile-overlaps-test
  (is (thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand "Overlaps")))))


;; 19.21. OverlapsBefore
;;
;; Normalized to ProperContains Start
(deftest compile-overlaps-before-test
  (is (thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand "OverlapsBefore")))))


;; 19.22. OverlapsAfter
;;
;; Normalized to ProperContains End
(deftest compile-overlaps-after-test
  (is (thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand "OverlapsAfter")))))


;; 19.23. PointFrom
;;
;; The PointFrom expression extracts the single point from the source interval.
;; The source interval must be a unit interval (meaning an interval with the
;; same starting and ending boundary), otherwise, a run-time error is thrown.
;;
;; If the source interval is null, the result is null.
(deftest compile-point-from-test
  (are [x res] (= res (-eval (compile {} {:type "PointFrom" :operand x}) {} nil nil))
    #elm/interval [#elm/int "1" #elm/int "1"] 1
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
        #elm/interval [#elm/int "1" #elm/int "3"] #elm/int "2" true
        #elm/interval [#elm/int "1" #elm/int "1"] #elm/int "1" false
        #elm/interval [#elm/int "1" #elm/int "1"] #elm/int "2" false))))


;; 19.25. ProperIn
;;
;; Normalized to ProperContains
(deftest compile-proper-in-test
  (is (thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand "ProperIn")))))


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
        #elm/interval [#elm/int "1" #elm/int "3"]
        #elm/interval [#elm/int "1" #elm/int "2"] true
        #elm/interval [#elm/int "1" #elm/int "2"]
        #elm/interval [#elm/int "1" #elm/int "2"] false))))


;; 19.27. ProperIncludedIn
;;
;; Normalized to ProperIncludes
(deftest compile-proper-included-in-test
  (is (thrown-with-msg? Exception #"Unsupported" (compile {} (binary-operand "ProperIncludedIn")))))


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
      #elm/interval [#elm/int "1" #elm/int "2"] 1
      #elm/interval [:< #elm/int "1" #elm/int "2"] 2
      #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"} #elm/int "2"] Integer/MIN_VALUE))

  (testing "Decimal"
    (are [x res] (= res (-eval (compile {} {:type "Start" :operand x}) {} nil nil))
      #elm/interval [#elm/dec "1.1" #elm/dec "2"] 1.1M
      #elm/interval [:< #elm/dec "1.1" #elm/dec "2"] 1.10000001M
      #elm/interval [{:type "Null" :resultTypeName "{urn:hl7-org:elm-types:r1}Decimal"} #elm/dec "2"] decimal/min)))


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
      {:type "Null"} #elm/interval [#elm/int "1" #elm/int "2"] nil
      #elm/interval [#elm/int "1" #elm/int "2"] {:type "Null"} nil))

  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} {:type "Starts" :operand [a b]}) {} nil nil))
      #elm/interval [#elm/int "1" #elm/int "3"]
      #elm/interval [#elm/int "1" #elm/int "3"] true
      #elm/interval [#elm/int "1" #elm/int "2"]
      #elm/interval [#elm/int "1" #elm/int "3"] true
      #elm/interval [#elm/int "2" #elm/int "3"]
      #elm/interval [#elm/int "1" #elm/int "3"] false)))


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
      #elm/list [#elm/int "1"] #elm/list [#elm/int "1"] [1]
      #elm/list [#elm/int "1"] #elm/list [#elm/int "2"] [1 2]
      #elm/list [#elm/int "1"] #elm/list [#elm/int "1" #elm/int "2"] [1 2]

      {:type "Null"} {:type "Null"} nil))

  (testing "Interval"
    (are [x y res] (= res (-eval (compile {} (elm/union [x y])) {} nil nil))
      #elm/interval [#elm/int "1" #elm/int "2"]
      #elm/interval [#elm/int "3" #elm/int "4"]
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
      #elm/interval [#elm/int "1" #elm/int "2"] 1)))



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

    #elm/list [#elm/int "1"]
    [1]

    #elm/list [#elm/int "1" {:type "Null"}]
    [1 nil]

    #elm/list [#elm/int "1" #elm/int "2"]
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
    #elm/list [#elm/int "1"] [1]
    #elm/list [#elm/int "1" #elm/int "1"] [1]
    #elm/list [#elm/int "1" #elm/int "1" #elm/int "2"] [1 2]
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
    #elm/list [#elm/int "1"] true
    #elm/list [#elm/int "1" #elm/int "1"] true
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
    #elm/list [#elm/int "1"] #elm/boolean "false" []
    #elm/list [#elm/int "1"] #elm/equal [#elm/current "A" #elm/int "1"] [1]

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
    #elm/list [#elm/int "1"] 1
    #elm/list [#elm/int "1" #elm/int "2"] 1

    {:type "Null"} nil))


;; 20.11. Flatten
;;
;; The Flatten operator flattens a list of lists into a single list.
;;
;; If the argument is null, the result is null.
(deftest compile-flatten-test
  (are [list res] (= res (-eval (compile {} (elm/flatten list)) {} nil nil))
    #elm/list [] []
    #elm/list [#elm/int "1"] [1]
    #elm/list [#elm/int "1" #elm/list [#elm/int "2"]] [1 2]
    #elm/list [#elm/int "1" #elm/list [#elm/int "2"] #elm/int "3"] [1 2 3]
    #elm/list [#elm/int "1" #elm/list [#elm/int "2" #elm/list [#elm/int "3"]]] [1 2 3]
    #elm/list [#elm/list [#elm/int "1" #elm/list [#elm/int "2"]] #elm/int "3"] [1 2 3]

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
      #elm/list [#elm/int "1"] {:type "Null"} [nil]

      {:type "Null"} {:type "Null"} nil))

  (testing "With scope"
    (are [source element res] (= res (-eval (compile {} {:type "ForEach" :source source :element element :scope "A"}) {} nil nil))
      #elm/list [#elm/int "1"] #elm/current "A" [1]
      #elm/list [#elm/int "1" #elm/int "2"] #elm/add [#elm/current "A" #elm/int "1"] [2 3]

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
    #elm/list [] #elm/int "1" -1
    #elm/list [#elm/int "1"] #elm/int "1" 0
    #elm/list [#elm/int "1" #elm/int "1"] #elm/int "1" 0
    #elm/list [#elm/int "1" #elm/int "2"] #elm/int "2" 1

    #elm/list [] {:type "Null"} nil
    {:type "Null"} #elm/int "1" nil
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
    #elm/list [#elm/int "1"] 1
    #elm/list [#elm/int "1" #elm/int "2"] 2

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
    #elm/list [#elm/int "1"] 1
    {:type "Null"} nil)

  (are [list] (thrown? Exception (-eval (compile {} (elm/singleton-from list)) {} nil nil))
    #elm/list [#elm/int "1" #elm/int "1"]))


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
    #elm/list [#elm/int "1"] #elm/int "0" #elm/int "1" [1]
    #elm/list [#elm/int "1" #elm/int "2"] #elm/int "0" #elm/int "1" [1]
    #elm/list [#elm/int "1" #elm/int "2"] #elm/int "1" #elm/int "2" [2]
    #elm/list [#elm/int "1" #elm/int "2" #elm/int "3"] #elm/int "1" #elm/int "3" [2 3]
    #elm/list [#elm/int "1" #elm/int "2"] {:type "Null"} {:type "Null"} [1 2]

    #elm/list [#elm/int "1"] #elm/int "-1" #elm/int "0" []
    #elm/list [#elm/int "1"] #elm/int "1" #elm/int "0" []


    {:type "Null"} #elm/int "0" #elm/int "0" nil
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
    #elm/list [#elm/int "2" #elm/int "1"]
    {:type "ByDirection" :direction "asc"} [1 2]
    #elm/list [#elm/int "1" #elm/int "2"]
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
      #elm/list [#elm/dec "1" #elm/dec "2"] 1.5M
      #elm/list [#elm/int "1" #elm/int "2"] 1.5M
      #elm/list [#elm/int "1"] 1M

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
      #elm/list [#elm/int "1"] 1
      #elm/list [#elm/int "1" #elm/int "1"] 2

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
      #elm/list [#elm/dec "2" #elm/dec "8"] 4M
      #elm/list [#elm/int "2" #elm/int "8"] 4M
      #elm/list [#elm/int "1"] 1M

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
      #elm/list [#elm/dec "2" #elm/dec "8"] 16M
      #elm/list [#elm/int "2" #elm/int "8"] 16
      #elm/list [#elm/int "1"] 1

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
      #elm/list [#elm/dec "2" #elm/dec "8"] 8M
      #elm/list [#elm/int "2" #elm/int "8"] 8
      #elm/list [#elm/int "1"] 1

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
      #elm/list [#elm/dec "2" #elm/dec "10" #elm/dec "8"] 8M
      #elm/list [#elm/int "2" #elm/int "10" #elm/int "8"] 8
      #elm/list [#elm/int "1" #elm/int "2"] 1.5M
      #elm/list [#elm/int "1"] 1

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
      #elm/list [#elm/dec "2" #elm/dec "8"] 2M
      #elm/list [#elm/int "2" #elm/int "8"] 2
      #elm/list [#elm/int "1"] 1

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
      #elm/list [#elm/dec "2" #elm/dec "2" #elm/dec "8"] 2M
      #elm/list [#elm/int "2" #elm/int "2" #elm/int "8"] 2
      #elm/list [#elm/int "1"] 1
      #elm/list [#elm/int "1" {:type "Null"} {:type "Null"}] 1

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
      #elm/list [#elm/dec "1" #elm/dec "2" #elm/dec "3" #elm/dec "4" #elm/dec "5"] 2M

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
      #elm/list [#elm/dec "1" #elm/dec "2" #elm/dec "3" #elm/dec "4" #elm/dec "5"] 1.41421356M

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
      #elm/list [#elm/dec "2" #elm/dec "8"] 10M
      #elm/list [#elm/int "2" #elm/int "8"] 10
      #elm/list [#elm/int "1"] 1

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
      #elm/list [#elm/dec "1" #elm/dec "2" #elm/dec "3" #elm/dec "4" #elm/dec "5"] 1.58113883M

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
      #elm/list [#elm/dec "1" #elm/dec "2" #elm/dec "3" #elm/dec "4" #elm/dec "5"] 2.5M

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
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/as ["{http://hl7.org/fhir}boolean" #elm/boolean "true"]
      true

      #elm/as ["{http://hl7.org/fhir}integer" #elm/int "1"]
      1

      #elm/as ["{http://hl7.org/fhir}string" #elm/string "a"]
      "a"

      #elm/as ["{http://hl7.org/fhir}decimal" #elm/dec "1.1"]
      1.1M

      #elm/as ["{http://hl7.org/fhir}uri" #elm/string "a"]
      "a"

      #elm/as ["{http://hl7.org/fhir}url" #elm/string "a"]
      "a"

      #elm/as ["{http://hl7.org/fhir}canonical" #elm/string "a"]
      "a"

      #elm/as ["{http://hl7.org/fhir}dateTime" #elm/date-time "2019-09-04"]
      (LocalDate/of 2019 9 4)

      #elm/as ["{http://hl7.org/fhir}Quantity" #elm/date-time "2019-09-04"]
      nil))

  (testing "ELM types"
    (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
      #elm/as ["{urn:hl7-org:elm-types:r1}Boolean" #elm/boolean "true"]
      true

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" #elm/int "1"]
      1

      #elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]
      nil

      #elm/as ["{urn:hl7-org:elm-types:r1}DateTime" #elm/date-time "2019-09-04"]
      (LocalDate/of 2019 9 4))))


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
  (are [elm res] (= res (-eval (compile {} {:type "Descendents" :source elm}) {:now now} nil nil))
    {:type "Null"} nil))


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
  (are [elm res] (= res (-eval (compile {} {:type "ToDate" :operand elm}) {:now now} nil nil))
    #elm/string "2019" (Year/of 2019)
    #elm/string "2019-01" (YearMonth/of 2019 1)
    #elm/string "2019-01-01" (LocalDate/of 2019 1 1)

    #elm/string "aaaa" nil
    #elm/string "2019-13" nil
    #elm/string "2019-02-29" nil

    #elm/date-time "2019-01-01T12:13" (LocalDate/of 2019 1 1)

    {:type "Null"} nil))


;; 22.22. ToDateTime
(deftest compile-to-date-time-test
  (are [elm res] (= res (-eval (compile {} elm) {:now now} nil nil))
    {:type "ToDateTime" :operand #elm/date "2019"}
    (Year/of 2019)))


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
  (are [x res] (= res (-eval (compile {} {:type "ToDecimal" :operand x}) {} nil nil))
    (elm/string (str decimal/min)) decimal/min
    #elm/string "-1.1" -1.1M
    #elm/string "-1" -1M
    #elm/string "0" 0M
    #elm/string "1" 1M
    (elm/string (str decimal/max)) decimal/max

    (elm/string (str (- decimal/min 1e-8M))) nil
    (elm/string (str (+ decimal/max 1e-8M))) nil
    #elm/string "a" nil

    #elm/int "1" 1M

    {:type "Null"} nil))


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
  (are [x res] (= res (-eval (compile {} {:type "ToInteger" :operand x}) {} nil nil))
    (elm/string (str Integer/MIN_VALUE)) Integer/MIN_VALUE
    #elm/string "-1" -1
    #elm/string "0" 0
    #elm/string "1" 1
    (elm/string (str Integer/MAX_VALUE)) Integer/MAX_VALUE

    (elm/string (str (dec Integer/MIN_VALUE))) nil
    (elm/string (str (inc Integer/MAX_VALUE))) nil
    #elm/string "a" nil

    #elm/int "1" 1

    {:type "Null"} nil))


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
  (are [elm res] (= res (-eval (compile {} elm) {} nil nil))
    {:type "ToList" :operand {:type "Null"}}
    []

    {:type "ToList" :operand #elm/boolean "false"}
    [false]

    {:type "ToList" :operand #elm/int "1"}
    [1]))


;; 22.26. ToQuantity
;;
;; The ToQuantity operator converts the value of its argument to a Quantity
;; value. The operator accepts strings using the following format:
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
;; If the argument is null, the result is null.
(deftest compile-to-quantity-test
  (are [x res] (= res (-eval (compile {} {:type "ToQuantity" :operand x}) {} nil nil))
    ; TODO (elm/string (str decimal/min)) decimal/min
    ; TODO #elm/string "-1" -1M
    ; TODO #elm/string "0" 0M
    ; TODO #elm/string "1" 1M
    ; TODO (elm/string (str decimal/max)) decimal/max

    ; TODO #elm/string "5.5 cm" (quantity 5.5M "cm")

    ; TODO (elm/string (str (- decimal/min 1e-8M))) nil
    ; TODO (elm/string (str (+ decimal/max 1e-8M))) nil
    ; TODO #elm/string "a" nil

    {:type "Null"} nil))


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
  (are [x res] (= res (-eval (compile {} {:type "ToString" :operand x}) {} nil nil))
    #elm/boolean "true" "true"
    #elm/boolean "false" "false"

    #elm/int "-1" "-1"
    #elm/int "0" "0"
    #elm/int "1" "1"

    #elm/dec "-1" "-1"
    #elm/dec "0" "0"
    #elm/dec "1" "1"

    #elm/dec "-1.1" "-1.1"
    #elm/dec "0.0" "0.0"
    #elm/dec "1.1" "1.1"

    #elm/dec "0.0001" "0.0001"
    #elm/dec "0.00001" "0.00001"
    #elm/dec "0.000001" "0.000001"
    #elm/dec "0.0000001" "0.0000001"
    #elm/dec "0.00000001" "0.00000001"
    #elm/dec "0.000000001" "0.00000000"
    #elm/dec "0.000000005" "0.00000001"

    #elm/quantity [1 "m"] "1 'm'"
    #elm/quantity [1M "m"] "1 'm'"
    #elm/quantity [1.1M "m"] "1.1 'm'"

    #elm/date "2019" "2019"
    #elm/date "2019-01" "2019-01"
    #elm/date "2019-01-01" "2019-01-01"

    #elm/date-time "2019-01-01T01:00" "2019-01-01T01:00"

    #elm/time "01:00" "01:00"

    {:type "Null"} nil))



;; 23. Clinical Operators

;; 23.4.
;;
;; Calculates the age in the specified precision of a person born on the first
;; Date or DateTime as of the second Date or DateTime.
;;
;; The CalculateAgeAt operator has two signatures: Date, Date DateTime, DateTime
;;
;; For the Date overload, precision must be one of year, month, week, or day.
;;
;; The result of the calculation is the number of whole calendar periods that
;; have elapsed between the first date/time and the second.
(deftest compile-calculate-age-at-test
  (testing "Null"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil nil))
      {:type "CalculateAgeAt" :operand [#elm/date "2018" {:type "Null"}]
       :precision "Year"}
      nil
      {:type "CalculateAgeAt" :operand [{:type "Null"} #elm/date "2018"]
       :precision "Year"}
      nil))

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
      nil)))
