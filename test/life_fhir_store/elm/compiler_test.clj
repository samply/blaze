(ns life-fhir-store.elm.compiler-test
  "Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [clojure.test.check :as tc]
    [clojure.test.check.properties :as prop]
    [life-fhir-store.datomic.cql :as cql]
    [life-fhir-store.datomic.time :as time]
    [life-fhir-store.datomic.quantity :as quantity]
    [life-fhir-store.elm.compiler
     :refer [compile compile-with-equiv-clause -eval -hash]]
    [life-fhir-store.elm.date-time :refer [period]]
    [life-fhir-store.elm.interval :refer [interval]]
    [life-fhir-store.elm.literals :as elm]
    [life-fhir-store.elm.quantity :refer [parse-quantity]])
  (:import
    [java.math BigDecimal]
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth
               ZoneOffset]
    [java.time.temporal Temporal]
    [javax.measure UnconvertibleException]
    [life_fhir_store.elm.date_time Period])
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


(def now (OffsetDateTime/now (ZoneOffset/ofHours 0)))


(defmacro satisfies-prop [num-tests prop]
  `(let [result# (tc/quick-check ~num-tests ~prop)]
     (if (instance? Throwable (:result result#))
       (throw (:result result#))
       (if (true? (:result result#))
         (is :success)
         (is (clojure.pprint/pprint result#))))))



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
      #elm/dec "-0.1" -0.1M
      #elm/dec "0" 0M
      #elm/dec "0.1" 0.1M
      #elm/dec "1" 1M))

  (testing "Integer Literal"
    (are [elm res] (= res (compile {} elm))
      #elm/int "-1" -1
      #elm/int "0" 0
      #elm/int "1" 1)))

;; 2. Structured Values

;; 2.1. Tuple
(deftest compile-tuple-test
  (are [m res] (= res (-eval (compile {} (elm/tuple m)) {} nil))
    {"id" #elm/int "1"} {:id 1}
    {"id" #elm/int "1" "name" #elm/string "john"} {:id 1 :name "john"}))


;; 2.3. Property
(deftest compile-property-test
  (testing "with entity supplied over query context"
    (are [elm entity result]
      (= result (-eval (compile {:eval-context "Population"} elm)
                       nil {"P" entity}))
      {:path "gender"
       :scope "P"
       :type "Property"
       :resultTypeName "{http://hl7.org/fhir}AdministrativeGender"
       :life/source-type "{http://hl7.org/fhir}Patient"}
      {:Patient/gender "male"}
      "male"))

  (testing "with entity supplied directly"
    (are [elm entity result]
      (= result (-eval (compile {:eval-context "Population"
                                 :life/single-query-scope "P"}
                                elm)
                       nil entity))
      {:path "gender"
       :scope "P"
       :type "Property"
       :resultTypeName "{http://hl7.org/fhir}AdministrativeGender"
       :life/source-type "{http://hl7.org/fhir}Patient"}
      {:Patient/gender "male"}
      "male"))

  (testing "with source"
    (are [elm source result]
      (= result (-eval (compile {:eval-context "Population"} elm)
                       {:library-context {"Patient" source}} nil))
      {:path "gender"
       :source {:name "Patient" :type "ExpressionRef"}
       :type "Property"
       :resultTypeName "{http://hl7.org/fhir}AdministrativeGender"
       :life/source-type "{http://hl7.org/fhir}Patient"}
      {:Patient/gender "male"}
      "male")))



;; 3. Clinical Values

;; 3.3. CodeRef
(deftest compile-code-ref-test
  (st/instrument
    `cql/find-coding
    {:spec
     {`cql/find-coding
      (s/fspec
        :args (s/cat :db #{::db} :system #{"life"} :code #{"0"})
        :ret #{::coding})}
     :stub #{`cql/find-coding}})

  (let [context
        {:library
         {:codeSystems {:def [{:name "life" :id "life" :accessLevel "Public"}]}
          :codes
          {:def
           [{:name "lens_0"
             :id "0"
             :accessLevel "Public"
             :codeSystem {:name "life"}}]}}}]
    (are [elm result] (= result (-eval (compile context elm) {:db ::db} nil))
      {:name "lens_0" :type "CodeRef"}
      ::coding)))


;; 3.9. Quantity
(deftest compile-quantity-test
  (testing "Examples"
    (are [elm res] (= res (compile {} elm))
      #elm/quantity [1] 1
      #elm/quantity [1 "year"] (period 1 0 0)
      #elm/quantity [2 "years"] (period 2 0 0)
      #elm/quantity [1 "month"] (period 0 1 0)
      #elm/quantity [2 "months"] (period 0 2 0)
      #elm/quantity [1 "week"] (period 0 0 (* 7 24 60 60))
      #elm/quantity [2 "weeks"] (period 0 0 (* 2 7 24 60 60))
      #elm/quantity [1 "day"] (period 0 0 (* 24 60 60))
      #elm/quantity [2 "days"] (period 0 0 (* 2 24 60 60))
      #elm/quantity [1 "hour"] (period 0 0 (* 60 60))
      #elm/quantity [2 "hours"] (period 0 0 (* 2 60 60))
      #elm/quantity [1 "minute"] (period 0 0 60)
      #elm/quantity [2 "minutes"] (period 0 0 (* 2 60))
      #elm/quantity [1 "second"] (period 0 0 1)
      #elm/quantity [2 "seconds"] (period 0 0 2)
      #elm/quantity [1 "s"] (parse-quantity 1 "s")
      #elm/quantity [1 "cm2"] (parse-quantity 1 "cm2")))

  (testing "Periods"
    (satisfies-prop 100
      (prop/for-all [period (s/gen :elm/period)]
        (#{BigDecimal Period} (type (-eval (compile {} period) {} {})))))))


;; 9. Reusing Logic

;; 9.2. ExpressionRef
(deftest compile-expression-ref-test
  (are [elm res]
    (= res (-eval (compile {} elm) {:library-context {"foo" ::result}} nil))
    {:type "ExpressionRef" :name "foo"}
    ::result))



;; 10. Queries

;; 10.1. Query
(deftest compile-query-test
  (st/instrument
    `cql/list-resource
    {:spec
     {`cql/list-resource
      (s/fspec
        :args (s/cat :db #{::db} :data-type-name #{"Patient"})
        :ret #{[::patient]})}
     :stub #{`cql/list-resource}})

  (let [retrieve {:dataType "{http://hl7.org/fhir}Patient" :type "Retrieve"}
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
    (are [elm res] (= res (-eval (compile {} elm) {:db ::db} nil))
      {:type "Query"
       :source
       [{:alias "P"
         :expression retrieve}]}
      #{::patient}

      {:type "Query"
       :source
       [{:alias "P"
         :expression retrieve}]
       :where where}
      #{}

      {:type "Query"
       :source
       [{:alias "P"
         :expression retrieve}]
       :where #elm/boolean "false"}
      #{}

      {:type "Query"
       :source
       [{:alias "P"
         :expression retrieve}]
       :return {:expression return}}
      #{nil}

      {:type "Query"
       :source
       [{:alias "P"
         :expression retrieve}]
       :where where
       :return {:expression return}}
      #{})))


;; 10.3. AliasRef
(deftest compile-alias-ref-test
  (are [elm res] (= res (-eval (compile {} elm) {} {"foo" ::result}))
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

  (st/instrument
    `cql/list-resource
    {:spec
     {`cql/list-resource
      (s/fspec
        :args (s/cat :db #{::db} :data-type-name #{"Observation"})
        :ret #{[{:Observation/subject ::subject}]})}
     :stub #{`cql/list-resource}})

  (testing "Equiv With with two Observations comparing there subjects."
    (let [elm {:alias "O1"
               :type "WithEquiv"
               :expression {:dataType "{http://hl7.org/fhir}Observation"
                            :type "Retrieve"}
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
          compile-context {:life/single-query-scope "O0"}
          create-clause (compile-with-equiv-clause compile-context elm)
          eval-context {:db ::db}
          eval-clause (create-clause eval-context)
          lhs-entity {:Observation/subject ::subject}]
      (is (true? (eval-clause eval-context lhs-entity)))))

  (testing "Equiv With with one Patient and one Observation comparing the patient with the operation subject."
    (let [elm {:alias "O"
               :type "WithEquiv"
               :expression {:dataType "{http://hl7.org/fhir}Observation"
                            :type "Retrieve"}
               :equivOperand [{:name "P" :type "AliasRef" :life/scopes #{"P"}}
                              {:path "subject"
                               :scope "O"
                               :type "Property"
                               :resultTypeName "{http://hl7.org/fhir}Reference"
                               :life/scopes #{"O"}
                               :life/source-type "{http://hl7.org/fhir}Observation"}]}
          compile-context {:life/single-query-scope "P"}
          create-clause (compile-with-equiv-clause compile-context elm)
          eval-context {:db ::db}
          eval-clause (create-clause eval-context)
          lhs-entity ::subject]
      (is (true? (eval-clause eval-context lhs-entity))))))


;; 11. External Data

;; 11.1. Retrieve
(deftest compile-retrieve-test
  (st/instrument
    `cql/find-coding
    {:spec
     {`cql/find-coding
      (s/fspec
        :args (s/cat :db #{::db} :system #{"life"} :code #{"0"})
        :ret #{::coding})}
     :stub #{`cql/find-coding}})

  (st/instrument
    `cql/list-resource
    {:spec
     {`cql/list-resource
      (s/fspec
        :args (s/cat :db #{::db} :data-type-name #{"Patient"})
        :ret #{[::patient]})}
     :stub #{`cql/list-resource}})

  (let [context
        {:library
         {:codeSystems {:def [{:name "life" :id "life" :accessLevel "Public"}]}
          :codes
          {:def
           [{:name "lens_0"
             :id "0"
             :accessLevel "Public"
             :codeSystem {:name "life"}}]}}}]

    (testing "in Patient eval context"

      (testing "while retrieving patients"
        (let [elm {:dataType "{http://hl7.org/fhir}Patient" :type "Retrieve"}]
          (testing "a singleton list of the current patient is returned"
            (is (= [::patient]
                   (-eval (compile (assoc context :eval-context "Patient") elm)
                          {:patient ::patient} nil))))))

      (testing "while retrieving observations"
        (let [elm {:dataType "{http://hl7.org/fhir}Observation" :type "Retrieve"}]
          (testing "the observations of the current patient are returned"
            (is (= [::observation]
                   (-eval (compile (assoc context :eval-context "Patient") elm)
                          {:patient {:Observation/_subject [::observation]}} nil))))))

      (testing "while retrieving observations with one specific code"
        (st/instrument
          `cql/list-patient-resource-by-code
          {:spec
           {`cql/list-patient-resource-by-code
            (s/fspec
              :args (s/cat :patient #{::patient}
                           :data-type-name #{"Observation"}
                           :code-property-name #{"code"}
                           :codings #{[::coding]})
              :ret #{[::observation]})}
           :stub #{`cql/list-patient-resource-by-code}})

        (let [elm {:dataType "{http://hl7.org/fhir}Observation"
                   :codeProperty "code"
                   :type "Retrieve"
                   :codes {:type "ToList"
                           :operand {:name "lens_0" :type "CodeRef"}}}]
          (testing "the observations with that code of the current patient are returned"
            (is (= [::observation]
                   (-eval (compile (assoc context :eval-context "Patient") elm)
                          {:db ::db :patient ::patient} nil)))))))

    (testing "Population Eval Context"
      (testing "retrieving all patients"
        (st/instrument
          `cql/list-resource
          {:spec
           {`cql/list-resource
            (s/fspec
              :args (s/cat :db #{::db} :data-type-name #{"Patient"})
              :ret #{[::patient]})}
           :stub #{`cql/list-resource}})

        (are [elm res]
          (= res (-eval (compile (assoc context :eval-context "Population") elm)
                        {:db ::db} nil))

          {:dataType "{http://hl7.org/fhir}Patient" :type "Retrieve"}
          [::patient]))

      (testing "retrieving all observations with a certain code"
        (st/instrument
          `cql/list-resource-by-code
          {:spec
           {`cql/list-resource-by-code
            (s/fspec
              :args (s/cat :db #{::db}
                           :data-type-name #{"Observation"}
                           :code-property-name #{"code"}
                           :codings #{[::coding]})
              :ret #{[::observation]})}
           :stub #{`cql/list-resource-by-code}})

        (are [elm res]
          (= res (-eval (compile (assoc context :eval-context "Population") elm)
                        {:db ::db} nil))

          {:dataType "{http://hl7.org/fhir}Observation"
           :codeProperty "code"
           :type "Retrieve"
           :codes {:type "ToList"
                   :operand {:name "lens_0" :type "CodeRef"}}}
          [::observation])))))



;; 12. Comparison Operators

;; 12.1. Equal
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
    (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
      #elm/int "1" #elm/int "1" true
      #elm/int "1" #elm/int "2" false

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
      #elm/dec "1.1" #elm/dec "1.1" true
      #elm/dec "1.1" #elm/dec "2.1" false

      {:type "Null"} #elm/dec "1.1" nil
      #elm/dec "1.1" {:type "Null"} nil))

  (testing "Mixed Integer Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
      #elm/int "1" #elm/dec "1" true
      #elm/dec "1" #elm/int "1" true))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
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
    (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
      #elm/date "2013" #elm/date "2013" true
      #elm/date "2012" #elm/date "2013" false
      #elm/date "2013" #elm/date "2012" false

      {:type "Null"} #elm/date "2013" nil
      #elm/date "2013" {:type "Null"} nil))

  (testing "Date with year-month precision"
    (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
      #elm/date "2013-01" #elm/date "2013-01" true
      #elm/date "2013-01" #elm/date "2013-02" false
      #elm/date "2013-02" #elm/date "2013-01" false

      {:type "Null"} #elm/date "2013-01" nil
      #elm/date "2013-01" {:type "Null"} nil))

  (testing "Date with full precision"
    (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
      #elm/date "2013-01-01"
      #elm/date "2013-01-01" true
      #elm/date "2013-01-01"
      #elm/date "2013-01-02" false
      #elm/date "2013-01-02"
      #elm/date "2013-01-01" false

      {:type "Null"} #elm/date "2013-01-01" nil
      #elm/date "2013-01-01" {:type "Null"} nil))

  (testing "Today() = Today()"
    (are [a b] (true? (-eval (compile {} (elm/equal [a b])) {:now now} nil))
      {:type "Today"} {:type "Today"}))

  (testing "DateTime with full precision (there is only one precision)"
    (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
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

  (testing "Time with full precision (there is only one precision)"
    (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"]
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"] true
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"]
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "16"] false
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "16"]
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"] false

      #elm/time [#elm/int "12" #elm/int "30" #elm/int "0"]
      #elm/time [#elm/int "12" #elm/int "30"] true

      #elm/time [#elm/int "12" #elm/int "0"]
      #elm/time [#elm/int "12"] true

      {:type "Null"} #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"] nil
      #elm/time [#elm/int "12" #elm/int "30" #elm/int "15"] {:type "Null"} nil)))


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
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil))
      {:type "Null"} {:type "Null"} true))

  (testing "Boolean"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil))
      #elm/boolean "true" #elm/boolean "true" true
      #elm/boolean "true" #elm/boolean "false" false

      {:type "Null"} #elm/boolean "true" false
      #elm/boolean "true" {:type "Null"} false))

  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil))
      #elm/int "1" #elm/int "1" true
      #elm/int "1" #elm/int "2" false

      {:type "Null"} #elm/int "1" false
      #elm/int "1" {:type "Null"} false))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil))
      #elm/dec "1.1" #elm/dec "1.1" true
      #elm/dec "1.1" #elm/dec "2.1" false

      {:type "Null"} #elm/dec "1.1" false
      #elm/dec "1.1" {:type "Null"} false))

  (testing "Mixed Integer Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil))
      #elm/int "1" #elm/dec "1" true
      #elm/dec "1" #elm/int "1" true))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/equivalent [a b])) {} nil))
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
      #elm/quantity [1 "s"] {:type "Null"} false)))


;; 12.3. Greater
(deftest compile-greater-test
  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil))
      #elm/int "2" #elm/int "1" true
      #elm/int "1" #elm/int "1" false

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil))
      #elm/dec "2" #elm/dec "1" true
      #elm/dec "1" #elm/dec "1" false

      {:type "Null"} #elm/dec "1" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "String"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil))
      #elm/string "b" #elm/string "a" true
      #elm/string "a" #elm/string "a" false

      {:type "Null"} #elm/string "a" nil
      #elm/string "a" {:type "Null"} nil))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil))
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
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil))
      #elm/date "2014" #elm/date "2013" true
      #elm/date "2013" #elm/date "2013" false

      {:type "Null"} #elm/date "2013" nil
      #elm/date "2013" {:type "Null"} nil))

  (testing "Comparing dates with mixed precisions (year and year-month) results in null."
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil))
      #elm/date "2013" #elm/date "2013-01" nil
      #elm/date "2013-01" #elm/date "2013" nil))

  (testing "Time"
    (are [a b res] (= res (-eval (compile {} (elm/greater [a b])) {} nil))
      #elm/time "00:00:01" #elm/time "00:00:00" true
      #elm/time "00:00:00" #elm/time "00:00:00" false

      {:type "Null"} #elm/time "00:00:00" nil
      #elm/time "00:00:00" {:type "Null"} nil)))


;; 12.4. GreaterOrEqual
(deftest compile-greater-or-equal-test
  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil))
      #elm/int "1" #elm/int "1" true
      #elm/int "2" #elm/int "1" true
      #elm/int "1" #elm/int "2" false

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil))
      #elm/dec "1.1" #elm/dec "1.1" true
      #elm/dec "2.1" #elm/dec "1.1" true
      #elm/dec "1.1" #elm/dec "2.1" false

      {:type "Null"} #elm/dec "1.1" nil
      #elm/dec "1.1" {:type "Null"} nil))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil))
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
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil))
      #elm/date "2013" #elm/date "2013" true
      #elm/date "2014" #elm/date "2013" true
      #elm/date "2013" #elm/date "2014" false

      #elm/date "2014-01" #elm/date "2014" nil
      #elm/date "2014" #elm/date "2014-01" nil

      {:type "Null"} #elm/date "2014" nil
      #elm/date "2014" {:type "Null"} nil))

  (testing "Time"
    (are [a b res] (= res (-eval (compile {} (elm/greater-or-equal [a b])) {} nil))
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
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil))
      #elm/int "1" #elm/int "2" true
      #elm/int "1" #elm/int "1" false

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil))
      #elm/dec "1" #elm/dec "2" true
      #elm/dec "1" #elm/dec "1" false

      {:type "Null"} #elm/dec "1" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "String"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil))
      #elm/string "a" #elm/string "b" true
      #elm/string "a" #elm/string "a" false

      {:type "Null"} #elm/string "a" nil
      #elm/string "a" {:type "Null"} nil))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil))
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
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil))
      #elm/date "2012" #elm/date "2013" true
      #elm/date "2013" #elm/date "2013" false

      {:type "Null"} #elm/date "2013" nil
      #elm/date "2013" {:type "Null"} nil))

  (testing "Comparing dates with mixed precisions (year and year-month) results in null."
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil))
      #elm/date "2013" #elm/date "2013-01" nil
      #elm/date "2013-01" #elm/date "2013" nil))

  (testing "Date with full precision"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {:now now} nil))
      #elm/date "2013-06-14" #elm/date "2013-06-15" true
      #elm/date "2013-06-15" #elm/date "2013-06-15" false

      {:type "Null"} #elm/date "2013-06-15" nil
      #elm/date "2013-06-15" {:type "Null"} nil))

  (testing "Comparing dates with mixed precisions (year-month and full) results in null."
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil))
      #elm/date "2013-01" #elm/date "2013-01-01" nil
      #elm/date "2013-01-01" #elm/date "2013-01" nil))

  (testing "DateTime with full precision (there is only one precision)"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {:now now} nil))
      #elm/date-time "2013-06-15T11" #elm/date-time "2013-06-15T12" true
      #elm/date-time "2013-06-15T12" #elm/date-time "2013-06-15T12" false))

  (testing "Time with full precision (there is only one precision)"
    (are [a b res] (= res (-eval (compile {} (elm/less [a b])) {} nil))
      #elm/time "12:30:14" #elm/time "12:30:15" true
      #elm/time "12:30:15" #elm/time "12:30:15" false

      {:type "Null"} #elm/time "12:30:15" nil
      #elm/time "12:30:15" {:type "Null"} nil)))


;; 12.6. LessOrEqual
(deftest compile-less-or-equal-test
  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil))
      #elm/int "1" #elm/int "1" true
      #elm/int "1" #elm/int "2" true

      {:type "Null"} #elm/int "2" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil))
      #elm/dec "1" #elm/dec "2" true

      {:type "Null"} #elm/dec "2" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil))
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
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil))
      #elm/date "2013-06-14" #elm/date "2013-06-15" true
      #elm/date "2013-06-16" #elm/date "2013-06-15" false
      #elm/date "2013-06-15" #elm/date "2013-06-15" true

      #elm/date "2013-06-15" #elm/date-time "2013-06-15T00" nil
      #elm/date-time "2013-06-15T00" #elm/date "2013-06-15" nil))

  (testing "Time"
    (are [a b res] (= res (-eval (compile {} (elm/less-or-equal [a b])) {} nil))
      #elm/time "00:00:00" #elm/time "00:00:00" true
      #elm/time "00:00:00" #elm/time "00:00:01" true
      #elm/time "00:00:01" #elm/time "00:00:00" false

      {:type "Null"} #elm/time "00:00:00" nil
      #elm/time "00:00:00" {:type "Null"} nil)))


;; 12.7. Not Equal
(deftest compile-not-equal-test
  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/not-equal [a b])) {} nil))
      #elm/int "1" #elm/int "2" true
      #elm/int "1" #elm/int "1" false

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/not-equal [a b])) {} nil))
      #elm/dec "1.1" #elm/dec "2.1" true
      #elm/dec "1.1" #elm/dec "1.1" false

      {:type "Null"} #elm/dec "1.1" nil
      #elm/dec "1.1" {:type "Null"} nil))

  (testing "Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/not-equal [a b])) {} nil))
      #elm/quantity [1] #elm/quantity [2] true
      #elm/quantity [1] #elm/quantity [1] false

      #elm/quantity [1 "s"] #elm/quantity [2 "s"] true
      #elm/quantity [1 "s"] #elm/quantity [1 "s"] false

      #elm/quantity [1 "m"] #elm/quantity [101 "cm"] true
      #elm/quantity [1 "m"] #elm/quantity [100 "cm"] false

      {:type "Null"} #elm/quantity [1] nil
      #elm/quantity [1] {:type "Null"} nil

      {:type "Null"} #elm/quantity [1 "s"] nil
      #elm/quantity [1 "s"] {:type "Null"} nil)))



;; 13. Logical Operators

;; 13.1. And
(deftest compile-and-test
  (are [a b c] (= c (-eval (compile {} {:type "And" :operand [a b]}) {} nil))
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
(deftest compile-implies-test
  (is (thrown? Exception (compile {} {:type "Implies"}))))


;; 13.3. Not
(deftest compile-not-test
  (are [a b] (= b (-eval (compile {} {:type "Not" :operand a}) {} nil))
    #elm/boolean "true" false
    #elm/boolean "false" true
    {:type "Null"} nil))


;; 13.4. Or
(deftest compile-or-test
  (are [a b c] (= c (-eval (compile {} {:type "Or" :operand [a b]}) {} nil))
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
(deftest compile-xor-test
  (is (thrown? Exception (compile {} {:type "Xor"}))))



;; 14. Nullological Operators

;; 14.1. Null
(deftest compile-null-test
  (is (nil? (compile {} {:type "Null"}))))


;; 14.2. Coalesce
;;
;; The Coalesce operator returns the first non-null result in a list of
;; arguments. If all arguments evaluate to null the result is null.
(deftest compile-coalesce-test
  (are [elm res] (= res (-eval (compile {} {:type "Coalesce" :operand elm}) {} nil))
    [] nil
    [{:type "Null"}] nil
    [#elm/boolean "false" #elm/boolean "true"] false
    [{:type "Null"} #elm/int "1" #elm/int "2"] 1
    [#elm/int "2"] 2))


;; 14.3. IsFalse
(deftest compile-is-false-test
  (are [elm res] (= res (-eval (compile {} {:type "IsFalse" :operand elm}) {} nil))
    #elm/boolean "true" false
    #elm/boolean "false" true
    {:type "Null"} false))


;; 14.4. IsNull
(deftest compile-is-null-test
  (are [elm res] (= res (-eval (compile {} {:type "IsNull" :operand elm}) {} nil))
    #elm/boolean "true" false
    #elm/boolean "false" false
    {:type "Null"} true))


;; 14.5. IsTrue
;;
;; The IsTrue operator determines whether or not its argument evaluates to true.
;; If the argument evaluates to true, the result is true; if the argument
;; evaluates to false or null, the result is false.
(deftest compile-is-true-test
  (are [elm res] (= res (-eval (compile {} {:type "IsTrue" :operand elm}) {} nil))
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
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    #elm/if [#elm/boolean "true" #elm/int "1" #elm/int "2"] 1
    #elm/if [#elm/boolean "false" #elm/int "1" #elm/int "2"] 2
    #elm/if [{:type "Null"} #elm/int "1" #elm/int "2"] 2))



;; 16. Arithmetic Operators

;; 16.1. Abs
;;
;; Abs(argument Integer) Integer
;; Abs(argument Decimal) Decimal
;; Abs(argument Quantity) Quantity
;;
;; The Abs operator returns the absolute value of its argument.
;;
;; When taking the absolute value of a quantity, the unit is unchanged.
;;
;; If the argument is null, the result is null.
;;
;; The Abs operator is defined for the Integer, Decimal, and Quantity types.
(deftest compile-abs-test
  (are [x res] (= res (-eval (compile {} {:type "Abs" :operand x}) {} nil))
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

    #elm/quantity [-1 "m"] (parse-quantity 1 "m")
    #elm/quantity [0 "m"] (parse-quantity 0 "m")
    #elm/quantity [1 "m"] (parse-quantity 1 "m")

    #elm/quantity [-1M "m"] (parse-quantity 1M "m")
    #elm/quantity [0M "m"] (parse-quantity 0M "m")
    #elm/quantity [1M "m"] (parse-quantity 1M "m")

    {:type "Null"} nil))


;; 16.2. Add
;;
;; +(left Integer, right Integer) Integer
;; +(left Decimal, right Decimal) Decimal
;; +(left Quantity, right Quantity) Quantity
;;
;; +(left Date, right Quantity) Date
;; +(left DateTime, right Quantity) DateTime
;; +(left Time, right Quantity) Time
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
    (are [a b res] (= res (-eval (compile {} (elm/add [a b])) {} nil))
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
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding zero decimal to any decimal doesn't change it"
    (satisfies-prop 100
      (prop/for-all [operand (s/gen :elm/decimal)]
        (let [elm (elm/equal [(elm/add [operand #elm/dec "0"]) operand])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding identical integers equals multiplying the same integer by two"
    (satisfies-prop 100
      (prop/for-all [integer (s/gen :elm/integer)]
        (let [elm (elm/equivalent [(elm/add [integer integer])
                                   (elm/multiply [integer #elm/int "2"])])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/add [a b])) {} nil))
      #elm/dec "-1.1" #elm/dec "-1.1" -2.2M
      #elm/dec "-1.1" #elm/dec "0" -1.1M
      #elm/dec "-1.1" #elm/dec "1.1" 0M
      #elm/dec "1.1" #elm/dec "0" 1.1M
      #elm/dec "1.1" #elm/dec "1.1" 2.2M

      {:type "Null"} #elm/dec "1" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "Adding identical decimals equals multiplying the same decimal by two"
    (satisfies-prop 100
      (prop/for-all [decimal (s/gen :elm/decimal)]
        (let [elm (elm/equal [(elm/add [decimal decimal])
                              (elm/multiply [decimal #elm/int "2"])])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding identical decimals and dividing by two results in the same decimal"
    (satisfies-prop 100
      (prop/for-all [decimal (s/gen :elm/decimal)]
        (let [elm (elm/equal [(elm/divide [(elm/add [decimal decimal])
                                           #elm/int "2"])
                              decimal])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Time-based quantity"
    (are [a b res] (= res (-eval (compile {} (elm/add [a b])) {} nil))
      #elm/quantity [1 "year"] #elm/quantity [1 "year"] (period 2 0 0)
      #elm/quantity [1 "year"] #elm/quantity [1 "month"] (period 1 1 0)
      #elm/quantity [1 "year"] #elm/quantity [1 "day"] (period 1 0 (* 24 3600))

      #elm/quantity [1 "day"] #elm/quantity [1 "day"] (period 0 0 (* 2 24 3600))
      #elm/quantity [1 "day"] #elm/quantity [1 "hour"] (period 0 0 (* 25 3600))

      #elm/quantity [1 "year"] #elm/quantity [1.1M "year"] (period 2.1M 0 0)
      #elm/quantity [1 "year"] #elm/quantity [13.1M "month"] (period 2 1.1M 0)))

  (testing "UCUM quantity"
    (are [a b res] (= res (-eval (compile {} (elm/add [a b])) {} nil))
      #elm/quantity [1 "m"] #elm/quantity [1 "m"] (parse-quantity 2 "m")
      #elm/quantity [1 "m"] #elm/quantity [1 "cm"] (parse-quantity 1.01M "m")))

  (testing "Incompatible UCUM Quantity Subtractions"
    (are [a b] (thrown? UnconvertibleException (-eval (compile {} (elm/add [a b])) {} nil))
      #elm/quantity [1 "cm2"] #elm/quantity [1 "cm"]
      #elm/quantity [1 "m"] #elm/quantity [1 "s"]))

  (testing "Adding identical quantities equals multiplying the same quantity with two"
    (satisfies-prop 100
      (prop/for-all [quantity (s/gen :elm/quantity)]
        (let [elm (elm/equal [(elm/add [quantity quantity])
                              (elm/multiply [quantity #elm/int "2"])])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding identical quantities and dividing by two results in the same quantity"
    (satisfies-prop 100
      (prop/for-all [quantity (s/gen :elm/quantity)]
        (let [elm (elm/equal [(elm/divide [(elm/add [quantity quantity])
                                           #elm/int "2"])
                              quantity])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Date + Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/add [a b])) {} nil))
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
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding a positive amount of years to a year-month makes it greater"
    (satisfies-prop 100
      (prop/for-all [year-month (s/gen :elm/year-month)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/greater [(elm/add [year-month years]) year-month])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding a positive amount of years to a date makes it greater"
    (satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/greater [(elm/add [date years]) date])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding a positive amount of years to a date-time makes it greater"
    (satisfies-prop 100
      (prop/for-all [date-time (s/gen :elm/literal-date-time)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/greater [(elm/add [date-time years]) date-time])]
          (true? (-eval (compile {} elm) {:now now} {}))))))

  (testing "Adding a positive amount of months to a year-month makes it greater"
    (satisfies-prop 100
      (prop/for-all [year-month (s/gen :elm/year-month)
                     months (s/gen :elm/pos-months)]
        (let [elm (elm/greater [(elm/add [year-month months]) year-month])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding a positive amount of months to a date makes it greater or lets it equal because a date can be also a year and adding a small amount of months to a year doesn't change it."
    (satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)
                     months (s/gen :elm/pos-months)]
        (let [elm (elm/greater-or-equal [(elm/add [date months]) date])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding a positive amount of months to a date-time makes it greater or lets it equal because a date-time can be also a year and adding a small amount of months to a year doesn't change it."
    (satisfies-prop 100
      (prop/for-all [date-time (s/gen :elm/literal-date-time)
                     months (s/gen :elm/pos-months)]
        (let [elm (elm/greater-or-equal [(elm/add [date-time months]) date-time])]
          (true? (-eval (compile {} elm) {:now now} {}))))))

  ;; TODO: is that right?
  (testing "Adding a positive amount of days to a year doesn't change it."
    (satisfies-prop 100
      (prop/for-all [year (s/gen :elm/year)
                     days (s/gen :elm/pos-days)]
        (let [elm (elm/equal [(elm/add [year days]) year])]
          (true? (-eval (compile {} elm) {} {}))))))

  ;; TODO: is that right?
  (testing "Adding a positive amount of days to a year-month doesn't change it."
    (satisfies-prop 100
      (prop/for-all [year-month (s/gen :elm/year-month)
                     days (s/gen :elm/pos-days)]
        (let [elm (elm/equal [(elm/add [year-month days]) year-month])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding a positive amount of days to a date makes it greater or lets it equal because a date can be also a year or year-month and adding any amount of days to a year or year-month doesn't change it."
    (satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)
                     days (s/gen :elm/pos-days)]
        (let [elm (elm/greater-or-equal [(elm/add [date days]) date])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Adding a positive amount of days to a date-time makes it greater or lets it equal because a date-time can be also a year or year-month and adding any amount of days to a year or year-month doesn't change it."
    (satisfies-prop 100
      (prop/for-all [date-time (s/gen :elm/literal-date-time)
                     days (s/gen :elm/pos-days)]
        (let [elm (elm/greater-or-equal [(elm/add [date-time days]) date-time])]
          (true? (-eval (compile {} elm) {:now now} {}))))))

  (testing "DateTime + Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/add [a b])) {} nil))
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "year"] (LocalDateTime/of 2020 1 1 0 0 0)
      #elm/date-time "2012-02-29T00" #elm/quantity [1 "year"] (LocalDateTime/of 2013 2 28 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "month"] (LocalDateTime/of 2019 2 1 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "day"] (LocalDateTime/of 2019 1 2 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "hour"] (LocalDateTime/of 2019 1 1 1 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "minute"] (LocalDateTime/of 2019 1 1 0 1 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "second"] (LocalDateTime/of 2019 1 1 0 0 1)))

  (testing "Time + Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/add [a b])) {} nil))
      #elm/time "00:00:00" #elm/quantity [1 "hour"] (LocalTime/of 1 0 0)
      #elm/time "00:00:00" #elm/quantity [1 "minute"] (LocalTime/of 0 1 0)
      #elm/time "00:00:00" #elm/quantity [1 "second"] (LocalTime/of 0 0 1)))

  (testing "Times are rings under addition."
    (satisfies-prop 100
      (prop/for-all [time (s/gen :elm/literal-time)
                     hours (s/gen :elm/pos-hours)]
        (let [elm (elm/less-or-equal [(elm/add [time hours]) #elm/time "23:59:59"])]
          (true? (-eval (compile {} elm) {} {})))))))


;; 16.3. Ceiling
;;
;; Ceiling(argument Decimal) Integer
;;
;; The Ceiling operator returns the first integer greater than or equal to the
;; argument.
;;
;; If the argument is null, the result is null.
(deftest compile-ceiling-test
  (are [x res] (= res (-eval (compile {} {:type "Ceiling" :operand x}) {} nil))
    #elm/int "1" 1

    #elm/dec "1.1" 2

    {:type "Null"} nil))


;; 16.4. Divide
;;
;; /(left Decimal, right Decimal) Decimal
;; /(left Quantity, right Quantity) Quantity
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
    (are [a b res] (= res (-eval (compile {} (elm/divide [a b])) {} nil))
      #elm/dec "1" #elm/dec "2" 0.5M
      #elm/dec "1.1" #elm/dec "2" 0.55M
      #elm/dec "10" #elm/dec "3" 3.33333333M

      #elm/dec "3" #elm/int "2" 1.5M

      #elm/dec "1" #elm/dec "0" nil

      #elm/dec "1.1" {:type "Null"} nil
      {:type "Null"} #elm/dec "1.1" nil))

  (testing "(d * d) / d = d"
    (satisfies-prop 100
      (prop/for-all [decimal (s/gen :elm/non-zero-decimal)]
        (let [elm (elm/equal [(elm/divide [(elm/multiply [decimal decimal]) decimal]) decimal])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "(d / d) * d = d"
    (satisfies-prop 100
      (prop/for-all [decimal (s/gen :elm/non-zero-decimal)]
        (let [elm (elm/equal [(elm/multiply [(elm/divide [decimal decimal]) decimal]) decimal])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "UCUM Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/divide [a b])) {} nil))
      #elm/quantity [1M "m"] #elm/int "2" (parse-quantity 0.5M "m")

      #elm/quantity [1 "m"] #elm/quantity [1 "s"] (parse-quantity 1 "m/s")
      #elm/quantity [1M "m"] #elm/quantity [1M "s"] (parse-quantity 1M "m/s")

      #elm/quantity [12 "cm2"] #elm/quantity [3 "cm"] (parse-quantity 4 "cm")

      #elm/quantity [1 "m"] {:type "Null"} nil
      {:type "Null"} #elm/quantity [1 "m"] nil)))


;; 16.5. Exp
;;
;; Exp(argument Decimal) Decimal
;;
;; The Exp operator returns e raised to the given power.
;;
;; If the argument is null, the result is null.
(deftest compile-exp-test
  (are [x res] (= res (-eval (compile {} {:type "Exp" :operand x}) {} nil))
    #elm/int "0" 1M
    #elm/dec "0" 1M
    {:type "Null"} nil))


;; 16.6. Floor
;;
;; Floor(argument Decimal) Integer
;;
;; The Floor operator returns the first integer less than or equal to the
;; argument.
;;
;; If the argument is null, the result is null.
(deftest compile-floor-test
  (are [x res] (= res (-eval (compile {} {:type "Floor" :operand x}) {} nil))
    #elm/int "1" 1
    #elm/dec "1.1" 1
    {:type "Null"} nil))


;; 16.7. Log
;;
;; Log(argument Decimal, base Decimal) Decimal
;;
;; The Log operator computes the logarithm of its first argument, using the
;; second argument as the base.
;;
;; If either argument is null, the result is null.
(deftest compile-log-test
  (are [x base res] (= res (-eval (compile {} {:type "Log" :operand [x base]}) {} nil))
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
;; Ln(argument Decimal) Decimal
;;
;; The Ln operator computes the natural logarithm of its argument.
;;
;; If the argument is null, the result is null.
;;
;; If the result of the operation cannot be represented, the result is null.
(deftest compile-ln-test
  (are [x res] (= res (-eval (compile {} {:type "Ln" :operand x}) {} nil))
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
  (are [type res] (= res (-eval (compile {} {:type "MaxValue" :valueType type}) {} nil))
    "{urn:hl7-org:elm-types:r1}Integer" Integer/MAX_VALUE
    "{urn:hl7-org:elm-types:r1}Decimal" (/ (- 1E28M 1) 1E8M)
    "{urn:hl7-org:elm-types:r1}Date" (LocalDate/of 9999 12 31)
    "{urn:hl7-org:elm-types:r1}DateTime" (LocalDateTime/of 9999 12 31 23 59 59 999000000)
    "{urn:hl7-org:elm-types:r1}Time" (LocalTime/of 23 59 59 999000000)))


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
  (are [type res] (= res (-eval (compile {} {:type "MinValue" :valueType type}) {} nil))
    "{urn:hl7-org:elm-types:r1}Integer" Integer/MIN_VALUE
    "{urn:hl7-org:elm-types:r1}Decimal" (/ (+ -1E28M 1) 1E8M)
    "{urn:hl7-org:elm-types:r1}Date" (LocalDate/of 1 1 1)
    "{urn:hl7-org:elm-types:r1}DateTime" (LocalDateTime/of 1 1 1 0 0 0 0)
    "{urn:hl7-org:elm-types:r1}Time" LocalTime/MIN))


;; 16.11. Modulo
;;
;; mod(left Integer, right Integer) Integer
;; mod(left Decimal, right Decimal) Decimal
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
  (are [x div res] (= res (-eval (compile {} {:type "Modulo" :operand [x div]}) {} nil))
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
;; *(left Integer, right Integer) Integer
;; *(left Decimal, right Decimal) Decimal
;; *(left Quantity, right Quantity) Quantity
;;
;; The Multiply operator performs numeric multiplication of its arguments.
;;
;; For multiplication operations involving quantities, the resulting quantity
;; will have the appropriate unit.
;;
;; If either argument is null, the result is null.
;;
;; The Multiply operator is defined for the Integer, Decimal and Quantity types.
(deftest compile-multiply-test
  (testing "Integer"
    (are [a b res] (= res (-eval (compile {} (elm/multiply [a b])) {} nil))
      #elm/int "1" #elm/int "2" 2

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/multiply [a b])) {} nil))
      #elm/dec "1" #elm/dec "2" 2M

      {:type "Null"} #elm/dec "1" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "UCUM Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/multiply [a b])) {} nil))
      #elm/quantity [1 "m"] #elm/int "2" (parse-quantity 2 "m")
      #elm/quantity [1 "m"] #elm/quantity [2 "m"] (parse-quantity 2 "m2")

      {:type "Null"} #elm/quantity [1 "m"] nil
      #elm/quantity [1 "m"] {:type "Null"} nil)))


;; 16.13. Negate
;;
;; -(argument Integer) Integer
;; -(argument Decimal) Decimal
;; -(argument Quantity) Quantity
;;
;; The Negate operator returns the negative of its argument.
;;
;; When negating quantities, the unit is unchanged.
;;
;; If the argument is null, the result is null.
;;
;; The Negate operator is defined for the Integer, Decimal, and Quantity types.
(deftest compile-negate-test
  (are [x res] (= res (-eval (compile {} {:type "Negate" :operand x}) {} nil))
    #elm/int "1" -1

    #elm/dec "1" -1M

    #elm/quantity [1] -1
    #elm/quantity [1M] -1M
    #elm/quantity [1 "m"] (parse-quantity -1 "m")
    #elm/quantity [1M "m"] (parse-quantity -1M "m")

    {:type "Null"} nil))


;; 16.14. Power
;;
;; ^(argument Integer, exponent Integer) Integer
;; ^(argument Decimal, exponent Decimal) Decimal
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
    (are [a b res] (= res (-eval (compile {} (elm/power [a b])) {} nil))
      #elm/int "10" #elm/int "2" 100
      #elm/int "2" #elm/int "-2" 0.25M

      {:type "Null"} #elm/int "1" nil
      #elm/int "1" {:type "Null"} nil))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/power [a b])) {} nil))
      #elm/dec "2.5" #elm/dec "2" 6.25M
      #elm/dec "10" #elm/dec "2" 100M
      #elm/dec "4" #elm/dec "0.5" 2M

      {:type "Null"} #elm/dec "1" nil
      #elm/dec "1" {:type "Null"} nil))

  (testing "Mixed"
    (are [a b res] (= res (-eval (compile {} (elm/power [a b])) {} nil))
      #elm/dec "2.5" #elm/int "2" 6.25M
      #elm/dec "10" #elm/int "2" 100M
      #elm/dec "10" #elm/int "2" 100M)))


;; 16.15. Predecessor
;;
;; predecessor of<T>(argument T) T
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
  (are [x res] (= res (-eval (compile {} (elm/predecessor x)) {} nil))
    #elm/int "0" -1
    #elm/dec "0" -1E-8M
    #elm/date "2019" (Year/of 2018)
    #elm/date "2019-01" (YearMonth/of 2018 12)
    #elm/date "2019-01-01" (LocalDate/of 2018 12 31)
    #elm/date-time "2019-01-01T00" (LocalDateTime/of 2018 12 31 23 59 59 999000000)
    #elm/time "12:00" (LocalTime/of 11 59 59 999000000)
    #elm/quantity [0 "m"] (parse-quantity -1 "m")
    #elm/quantity [0M "m"] (parse-quantity -1E-8M "m")
    {:type "Null"} nil)

  (are [x] (thrown? Exception (-eval (compile {} (elm/predecessor x)) {} nil))
    #elm/date "0001"
    #elm/date "0001-01"
    #elm/date "0001-01-01"
    #elm/time "00:00:00.0"
    #elm/date-time "0001-01-01T00:00:00.0"))


;; 16.16. Round
;;
;; Round(argument Decimal) Decimal
;; Round(argument Decimal, precision Integer) Decimal
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
    (are [x res] (= res (-eval (compile {} (elm/round [x])) {} nil))
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
    (are [x precision res] (= res (-eval (compile {} (elm/round [x precision])) {} nil))
      #elm/dec "3.14159" #elm/int "3" 3.142M))

  (testing "With non-literal precision"
    (are [x precision res] (= res (-eval (compile {} (elm/round [x precision])) {} nil))
      #elm/dec "3.14159" #elm/add [#elm/int "2" #elm/int "1"] 3.142M)))


;; 16.17. Subtract
;;
;; -(left Integer, right Integer) Integer
;; -(left Decimal, right Decimal) Decimal
;; -(left Quantity, right Quantity) Quantity
;;
;; -(left Date, right Quantity) Date
;; -(left DateTime, right Quantity) DateTime
;; -(left Time, right Quantity) Time
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
    (are [a b res] (= res (-eval (compile {} (elm/subtract [a b])) {} nil))
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
        (zero? (-eval (compile {} (elm/subtract [integer integer])) {} {})))))

  (testing "Decimal"
    (are [a b res] (= res (-eval (compile {} (elm/subtract [a b])) {} nil))
      #elm/dec "-1" #elm/dec "-1" 0M
      #elm/dec "-1" #elm/dec "0" -1M
      #elm/dec "1" #elm/dec "1" 0M
      #elm/dec "1" #elm/dec "0" 1M
      #elm/dec "1" #elm/dec "-1" 2M

      {:type "Null"} #elm/dec "1.1" nil
      #elm/dec "1.1" {:type "Null"} nil))

  (testing "Subtracting identical decimals results in zero"
    (satisfies-prop 100
      (prop/for-all [decimal (s/gen :elm/decimal)]
        (zero? (-eval (compile {} (elm/subtract [decimal decimal])) {} {})))))

  (testing "Time-based quantity"
    (are [a b res] (= res (-eval (compile {} (elm/subtract [a b])) {} nil))
      #elm/quantity [1 "year"] #elm/quantity [1 "year"] (period 0 0 0)
      #elm/quantity [1 "year"] #elm/quantity [1 "month"] (period 0 11 0)
      #elm/quantity [1 "year"] #elm/quantity [1 "day"] (period 1 0 (- (* 24 3600)))

      #elm/quantity [1 "day"] #elm/quantity [1 "day"] (period 0 0 0)
      #elm/quantity [1 "day"] #elm/quantity [1 "hour"] (period 0 0 (* 23 3600))

      #elm/quantity [1 "year"] #elm/quantity [1.1M "year"] (period -0.1M 0 0)
      #elm/quantity [1 "year"] #elm/quantity [13.1M "month"] (period 0 -1.1M 0)))

  (testing "UCUM quantity"
    (are [a b res] (= res (-eval (compile {} (elm/subtract [a b])) {} nil))
      #elm/quantity [1 "m"] #elm/quantity [1 "m"] (parse-quantity 0 "m")
      #elm/quantity [1 "m"] #elm/quantity [1 "cm"] (parse-quantity 0.99 "m")))

  (testing "Incompatible UCUM Quantity Subtractions"
    (are [a b] (thrown? UnconvertibleException (-eval (compile {} (elm/subtract [a b])) {} nil))
      #elm/quantity [1 "cm2"] #elm/quantity [1 "cm"]
      #elm/quantity [1 "m"] #elm/quantity [1 "s"]))

  (testing "Subtracting identical quantities results in zero"
    (satisfies-prop 100
      (prop/for-all [quantity (s/gen :elm/quantity)]
        ;; Can't test for zero because can't extract value from quantity
        ;; so use negate trick
        (let [elm (elm/equal [(elm/negate (elm/subtract [quantity quantity]))
                              (elm/subtract [quantity quantity])])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Date - Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/subtract [a b])) {} nil))
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

  (testing "Subtracting a positive amount of years from a year makes it smaller"
    (satisfies-prop 100
      (prop/for-all [year (s/gen :elm/year)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/less [(elm/subtract [year years]) year])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Subtracting a positive amount of years from a year-month makes it smaller"
    (satisfies-prop 100
      (prop/for-all [year-month (s/gen :elm/year-month)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/less [(elm/subtract [year-month years]) year-month])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Subtracting a positive amount of years from a date makes it smaller"
    (satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)
                     years (s/gen :elm/pos-years)]
        (let [elm (elm/less [(elm/subtract [date years]) date])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Subtracting a positive amount of months from a year-month makes it smaller"
    (satisfies-prop 100
      (prop/for-all [year-month (s/gen :elm/year-month)
                     months (s/gen :elm/pos-months)]
        (let [elm (elm/less [(elm/subtract [year-month months]) year-month])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Subtracting a positive amount of months from a date makes it smaller or lets it equal because a date can be also a year and subtracting a small amount of months from a year doesn't change it."
    (satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)
                     months (s/gen :elm/pos-months)]
        (let [elm (elm/less-or-equal [(elm/subtract [date months]) date])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "Subtracting a positive amount of days from a date makes it smaller or lets it equal because a date can be also a year or year-month and subtracting any amount of days from a year or year-month doesn't change it."
    (satisfies-prop 100
      (prop/for-all [date (s/gen :elm/literal-date)
                     days (s/gen :elm/pos-days)]
        (let [elm (elm/less-or-equal [(elm/subtract [date days]) date])]
          (true? (-eval (compile {} elm) {} {}))))))

  (testing "DateTime - Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/subtract [a b])) {} nil))
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "year"] (LocalDateTime/of 2018 1 1 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "month"] (LocalDateTime/of 2018 12 1 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "day"] (LocalDateTime/of 2018 12 31 0 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "hour"] (LocalDateTime/of 2018 12 31 23 0 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "minute"] (LocalDateTime/of 2018 12 31 23 59 0)
      #elm/date-time "2019-01-01T00" #elm/quantity [1 "second"] (LocalDateTime/of 2018 12 31 23 59 59)))

  (testing "Time - Quantity"
    (are [a b res] (= res (-eval (compile {} (elm/subtract [a b])) {} nil))
      #elm/time "00:00:00" #elm/quantity [1 "hour"] (LocalTime/of 23 0 0)
      #elm/time "00:00:00" #elm/quantity [1 "minute"] (LocalTime/of 23 59 0)
      #elm/time "00:00:00" #elm/quantity [1 "second"] (LocalTime/of 23 59 59)))

  (testing "Times are rings under subtraction."
    (satisfies-prop 100
      (prop/for-all [time (s/gen :elm/literal-time)
                     hours (s/gen :elm/pos-hours)]
        (let [elm (elm/greater-or-equal [(elm/subtract [time hours]) #elm/time "00:00:00"])]
          (true? (-eval (compile {} elm) {} {})))))))


;; 16.18. Successor
;;
;; successor of<T>(argument T) T
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
  (are [x res] (= res (-eval (compile {} (elm/successor x)) {} nil))
    #elm/int "0" 1
    #elm/dec "0" 1E-8M
    #elm/date "2019" (Year/of 2020)
    #elm/date "2019-01" (YearMonth/of 2019 2)
    #elm/date "2019-01-01" (LocalDate/of 2019 1 2)
    #elm/date-time "2019-01-01T00" (LocalDateTime/of 2019 1 1 0 0 0 1000000)
    #elm/time "00:00:00" (LocalTime/of 0 0 0 1000000)
    #elm/quantity [0 "m"] (parse-quantity 1 "m")
    #elm/quantity [0M "m"] (parse-quantity 1E-8M "m")
    {:type "Null"} nil)

  (are [x] (thrown? Exception (-eval (compile {} (elm/successor x)) {} nil))
    #elm/date "9999"
    #elm/date "9999-12"
    #elm/date "9999-12-31"
    #elm/time "23:59:59.999"
    #elm/date-time "9999-12-31T23:59:59.999"))


;; 16.19. Truncate
;;
;; Truncate(argument Decimal) Integer
;;
;; The Truncate operator returns the integer component of its argument.
;;
;; If the argument is null, the result is null.
(deftest compile-truncate-test
  (are [x res] (= res (-eval (compile {} (elm/truncate x)) {} nil))
    #elm/int "1" 1
    #elm/dec "1.1" 1
    {:type "Null"} nil))


;; 16.20. TruncatedDivide
;;
;; div(left Integer, right Integer) Integer
;; div(left Decimal, right Decimal) Decimal
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
  (are [num div res] (= res (-eval (compile {} (elm/truncated-divide [num div])) {} nil))
    #elm/int "1" #elm/int "2" 0
    #elm/int "2" #elm/int "2" 1

    #elm/dec "4.14" #elm/dec "2.06" 2M

    #elm/int "1" #elm/int "0" nil

    {:type "Null"} #elm/int "1" nil
    #elm/int "1" {:type "Null"} nil))


;; 17. String Operators

;; 17.4. Equal
(deftest compile-equal-string-test
  (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
    #elm/string "a" #elm/string "a" true
    #elm/string "a" #elm/string "b" false

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil))


;; 17.11. Not Equal
(deftest compile-not-equal-string-test
  (are [a b res] (= res (-eval (compile {} (elm/not-equal [a b])) {} nil))
    #elm/string "a" #elm/string "b" true
    #elm/string "a" #elm/string "a" false

    {:type "Null"} #elm/string "a" nil
    #elm/string "a" {:type "Null"} nil))



;; 18. Date and Time Operators

;; 18.4. Equal
(deftest compile-equal-date-time-test
  (testing "date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/equal [#elm/date "2012" #elm/date "2012"] true
      #elm/equal [#elm/date "2012" #elm/date "2013"] false
      #elm/equal [{:type "Null"} #elm/date "2012"] nil
      #elm/equal [#elm/date "2012" {:type "Null"}] nil)))


;; 18.6. Date
(deftest compile-date-test
  (testing "literal year"
    (are [elm res] (= res (compile {} elm))
      #elm/date "2019"
      (Year/of 2019)))

  (testing "non-literal year"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/date [#elm/add [#elm/int "2018" #elm/int "1"]]
      (Year/of 2019)))

  (testing "literal year-month"
    (are [elm res] (= res (compile {} elm))
      #elm/date "2019-03"
      (YearMonth/of 2019 3)))

  (testing "non-literal year-month"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/date [#elm/int "2019" #elm/add [#elm/int "2" #elm/int "1"]]
      (YearMonth/of 2019 3)))

  (testing "literal date"
    (are [elm res] (= res (compile {} elm))
      #elm/date "2019-03-23"
      (LocalDate/of 2019 3 23)))

  (testing "non-literal date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
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
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/date-time [#elm/as ["{urn:hl7-org:elm-types:r1}Integer" {:type "Null"}]] nil))

  (testing "non-literal year"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/date-time [#elm/add [#elm/int "2018" #elm/int "1"]]
      (Year/of 2019)))

  (testing "literal year-month"
    (are [elm res] (= res (compile {} elm))
      #elm/date-time "2019-03"
      (YearMonth/of 2019 3)))

  (testing "non-literal year-month"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/date-time [#elm/int "2019" #elm/add [#elm/int "2" #elm/int "1"]]
      (YearMonth/of 2019 3)))

  (testing "literal date"
    (are [elm res] (= res (compile {} elm))
      #elm/date-time "2019-03-23"
      (LocalDate/of 2019 3 23)))

  (testing "non-literal date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/date-time [#elm/int "2019" #elm/int "3"
                      #elm/add [#elm/int "22" #elm/int "1"]]
      (LocalDate/of 2019 3 23)))

  (testing "literal hour"
    (are [elm res] (= res (compile {} elm))
      #elm/date-time "2019-03-23T12"
      (LocalDateTime/of 2019 3 23 12 0 0)))

  (testing "non-literal hour"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
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
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
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
      (LocalDateTime/of 2019 3 23 10 13 14)))

  (testing "with decimal offset"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date-time [#elm/int "2019" #elm/int "3" #elm/int "23"
                      #elm/int "12" #elm/int "13" #elm/int "14" #elm/int "0"
                      #elm/dec "1.5"]
      (LocalDateTime/of 2019 3 23 10 43 14)))

  (testing "an ELM date-time (only literals) always evaluates to something implementing Temporal"
    (satisfies-prop 100
      (prop/for-all [date-time (s/gen :elm/literal-date-time)]
        (instance? Temporal (-eval (compile {} date-time) {:now now} nil))))))


;; 18.11. DurationBetween
(deftest compile-duration-between-test
  (testing "Year precision"
    (are [a b res] (= res (-eval (compile {} (elm/duration-between [a b "Year"])) {} nil))
      #elm/date "2018" #elm/date "2019" 1
      #elm/date "2018" #elm/date "2017" -1
      #elm/date "2018" #elm/date "2018" 0))

  (testing "Month precision"
    (are [a b res] (= res (-eval (compile {} (elm/duration-between [a b "Month"])) {} nil))
      #elm/date "2018-01" #elm/date "2018-02" 1
      #elm/date "2018-01" #elm/date "2017-12" -1
      #elm/date "2018-01" #elm/date "2018-01" 0))

  (testing "Day precision"
    (are [a b res] (= res (-eval (compile {} (elm/duration-between [a b "Day"])) {:now now} nil))
      #elm/date "2018-01-01" #elm/date "2018-01-02" 1
      #elm/date "2018-01-01" #elm/date "2017-12-31" -1
      #elm/date "2018-01-01" #elm/date "2018-01-01" 0))

  (testing "Hour precision"
    (are [a b res] (= res (-eval (compile {} (elm/duration-between [a b "Hour"])) {:now now} nil))
      #elm/date-time "2018-01-01T00" #elm/date-time "2018-01-01T01" 1
      #elm/date-time "2018-01-01T00" #elm/date-time "2017-12-31T23" -1
      #elm/date-time "2018-01-01T00" #elm/date-time "2018-01-01T00" 0))

  (testing "Calculating the duration between temporals with insufficient precision results in null."
    (are [a b p] (nil? (-eval (compile {} (elm/duration-between [a b p])) {} nil))
      #elm/date "2018" #elm/date "2018" "Month"
      #elm/date "2018-01" #elm/date "2018-01" "Day"
      #elm/date "2018-01-01" #elm/date "2018-01-01" "Hour")))


;; 18.12. Not Equal
(deftest compile-not-equal-date-time-test
  (testing "Date with year precision"
    (are [a b res] (= res (-eval (compile {} (elm/not-equal [a b])) {} nil))
      #elm/date "2012" #elm/date "2013" true
      #elm/date "2012" #elm/date "2012" false

      {:type "Null"} #elm/date "2012" nil
      #elm/date "2012" {:type "Null"} nil)))


;; 18.13. Now
(deftest compile-now-test
  (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
    {:type "Now"}
    now))


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
;;
;; TODO: we don't support precision yet
(deftest compile-time-test
  (testing "literal hour"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/int "12"]
      (LocalTime/of 12 00)))

  (testing "non-literal hour"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/time [#elm/add [#elm/int "11" #elm/int "1"]]
      (LocalTime/of 12 00)))

  (testing "literal hour-minute"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/int "12" #elm/int "13"]
      (LocalTime/of 12 13)))

  (testing "non-literal hour-minute"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/time [#elm/int "12" #elm/add [#elm/int "12"
                                         #elm/int "1"]]
      (LocalTime/of 12 13)))

  (testing "literal hour-minute-second"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/int "12" #elm/int "13" #elm/int "14"]
      (LocalTime/of 12 13 14)))

  (testing "non-literal hour-minute-second"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/time [#elm/int "12" #elm/int "13"
                 #elm/add [#elm/int "13" #elm/int "1"]]
      (LocalTime/of 12 13 14)))

  (testing "literal hour-minute-second-millisecond"
    (are [elm res] (= res (compile {} elm))
      #elm/time [#elm/int "12" #elm/int "13" #elm/int "14"
                 #elm/int "15"]
      (LocalTime/of 12 13 14 (* 15 1000 1000))))

  (testing "non-literal hour-minute-second-millisecond"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/time [#elm/int "12" #elm/int "13" #elm/int "14"
                 #elm/add [#elm/int "14" #elm/int "1"]]
      (LocalTime/of 12 13 14 (* 15 1000 1000))))

  (testing "an ELM time (only literals) always compiles to a LocalTime"
    (satisfies-prop 100
      (prop/for-all [time (s/gen :elm/time)]
        (instance? LocalTime (compile {} time)))))

  )
(comment (s/exercise :elm/time))


;; 18.22. Today
(deftest compile-today-test
  (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
    {:type "Today"}
    (.toLocalDate now)))



;; 19. Interval Operators

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
    (are [elm res] (= res (compile {} elm))
      #elm/interval [#elm/int "1" #elm/int "2"] (interval 1 2 true true)
      #elm/interval [#elm/dec "1" #elm/dec "2"] (interval 1M 2M true true))))


;; 19.15. Intersect
(deftest compile-intersect-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    {:type "Intersect"
     :operand [{:type "Null"}]}
    nil

    {:type "Intersect"
     :operand [{:type "Null"} {:type "Null"}]}
    nil

    {:type "Intersect"
     :operand [#elm/list [{:type "Null"}]]}
    #{nil}

    {:type "Intersect"
     :operand [#elm/list [#elm/int "1"]]}
    #{1}

    {:type "Intersect"
     :operand
     [#elm/list [#elm/int "1"]
      #elm/list [#elm/int "1" #elm/int "2"]]}
    #{1}))


;; 19.30. Union
(deftest compile-union-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    {:type "Union"
     :operand [{:type "Null"}]}
    nil

    {:type "Union"
     :operand [{:type "Null"} {:type "Null"}]}
    nil

    {:type "Union"
     :operand [#elm/list [{:type "Null"}]]}
    #{nil}

    {:type "Union"
     :operand [#elm/list [#elm/int "1"]]}
    #{1}

    {:type "Union"
     :operand
     [#elm/list [#elm/int "1"]
      #elm/list [#elm/int "2"]]}
    #{1 2}

    {:type "Union"
     :operand
     [#elm/list [#elm/int "1"]
      #elm/list [#elm/int "1" #elm/int "2"]]}
    #{1 2}))



;; 20. List Operators

;; 20.1. List
(deftest compile-list-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
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


;; 20.5. Equal
(deftest compile-equal-list-test
  (are [a b res] (= res (-eval (compile {} (elm/equal [a b])) {} nil))
    #elm/list [#elm/int "1"] #elm/list [#elm/int "1"] true
    #elm/list [#elm/int "1"] #elm/list [] false
    #elm/list [#elm/int "1"] #elm/list [#elm/int "2"] false

    {:type "Null"} #elm/list [] nil
    #elm/list [] {:type "Null"} nil))


;; 20.19. Not Equal
(deftest compile-not-equal-list-test
  (are [a b res] (= res (-eval (compile {} (elm/not-equal [a b])) {} nil))
    #elm/list [#elm/int "1"] #elm/list [] true
    #elm/list [#elm/int "1"] #elm/list [#elm/int "2"] true
    #elm/list [#elm/int "1"] #elm/list [#elm/int "1"] false

    {:type "Null"} #elm/list [] nil
    #elm/list [] {:type "Null"} nil))


;; 20.25. SingletonFrom
(deftest compile-singleton-from-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    {:type "SingletonFrom"
     :operand {:type "Null"}}
    nil

    {:type "SingletonFrom"
     :operand #elm/list []}
    nil

    {:type "SingletonFrom"
     :operand #elm/list [#elm/int "1"]}
    1)

  (let [elm {:type "SingletonFrom"
             :operand #elm/list [#elm/int "1" #elm/int "1"]}
        expr (compile {} elm)]
    (is (thrown? Exception (-eval expr {} nil))))

  (st/instrument
    `cql/list-resource
    {:spec
     {`cql/list-resource
      (s/fspec
        :args (s/cat :db #{::db} :data-type-name #{"Patient"})
        :ret #{[::patient]})}
     :stub #{`cql/list-resource}})

  (let [retrieve {:dataType "{http://hl7.org/fhir}Patient" :type "Retrieve"}]
    (are [elm res] (= res (-eval (compile {} elm) {:db ::db} nil))
      {:type "SingletonFrom"
       :operand retrieve}
      ::patient)))



;; 21. Aggregate Operators

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
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    {:type "Count"
     :source #elm/list [{:type "Null"}]}
    0

    {:type "Count"
     :source {:type "Null"}}
    0

    {:type "Count"
     :source #elm/list []}
    0

    {:type "Count"
     :source #elm/list [#elm/int "1"]}
    1

    {:type "Count"
     :source #elm/list [#elm/int "1" #elm/int "1"]}
    2))



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
  (are [elm input res] (= res (-eval (compile {} elm) {} {"I" input}))
    {:asType "{http://hl7.org/fhir}Quantity"
     :type "As"
     :operand
     {:path "value"
      :scope "I"
      :type "Property"
      :resultTypeSpecifier
      {:type [{:name "{http://hl7.org/fhir}Quantity",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}CodeableConcept",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}string",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}boolean",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}Range",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}Ratio",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}SampledData",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}Attachment",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}time",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}dateTime",
               :type "NamedTypeSpecifier"}
              {:name "{http://hl7.org/fhir}Period",
               :type "NamedTypeSpecifier"}]}
      :life/source-type "{http://hl7.org/fhir}Observation"}}
    {:Observation/valueQuantity (quantity/write 1.0)}
    1.0

    {:asType "{http://hl7.org/fhir}dateTime"
     :type "As"
     :operand
     {:path "effective"
      :scope "I"
      :type "Property"
      :resultTypeSpecifier
      {:type
       [{:name "{http://hl7.org/fhir}dateTime",
         :type "NamedTypeSpecifier"}
        {:name "{http://hl7.org/fhir}Period",
         :type "NamedTypeSpecifier"}
        {:name "{http://hl7.org/fhir}Timing",
         :type "NamedTypeSpecifier"}
        {:name "{http://hl7.org/fhir}instant",
         :type "NamedTypeSpecifier"}]}
      :life/source-type "{http://hl7.org/fhir}Observation"}}
    {:Observation/effectiveDateTime (time/write (Year/of 2012))}
    (Year/of 2012)))


;; 22.19. ToDate
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
  (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
    {:type "ToDate" :operand {:type "Null"}}
    nil

    {:type "ToDate" :operand #elm/string "2019"}
    (Year/of 2019)

    {:type "ToDate" :operand #elm/string "2019-01"}
    (YearMonth/of 2019 1)

    {:type "ToDate" :operand #elm/string "2019-01-01"}
    (LocalDate/of 2019 1 1)

    {:type "ToDate" :operand #elm/string "aaaa"}
    nil

    {:type "ToDate" :operand #elm/string "2019-13"}
    nil

    {:type "ToDate" :operand #elm/string "2019-02-29"}
    nil

    {:type "ToDate" :operand #elm/date "2019"}
    (Year/of 2019)

    {:type "ToDate" :operand #elm/date-time [#elm/int "2019" #elm/int "1" #elm/int "1" #elm/int "12" #elm/int "13" #elm/int "14"]}
    (LocalDate/of 2019 1 1)))


;; 22.20. ToDateTime
(deftest compile-to-date-time-test
  (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
    {:type "ToDateTime" :operand #elm/date "2019"}
    (Year/of 2019)))


;; 22.21. ToDecimal
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
  (are [x res] (= res (-eval (compile {} {:type "ToDecimal" :operand x}) {} nil))
    #elm/string "0" 0M
    #elm/string "1" 1M
    #elm/string "1.1" 1.1M
    #elm/string "a" nil

    #elm/int "1" 1M

    {:type "Null"} nil))


;; 22.23. ToList
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
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    {:type "ToList" :operand {:type "Null"}}
    []

    {:type "ToList" :operand #elm/boolean "false"}
    [false]

    {:type "ToList" :operand #elm/int "1"}
    [1]))



;; 23. Clinical Operators

;; 23.4.
;;;
;;; Calculates the age in the specified precision of a person born on the first
;;; Date or DateTime as of the second Date or DateTime.
;;;
;;; The CalculateAgeAt operator has two signatures: Date, Date DateTime, DateTime
;;;
;;; For the Date overload, precision must be one of year, month, week, or day.
;;;
;;; The result of the calculation is the number of whole calendar periods that
;;; have elapsed between the first date/time and the second.
(deftest compile-calculate-age-at-test
  (testing "Null"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      {:type "CalculateAgeAt" :operand [#elm/date "2018" {:type "Null"}]
       :precision "Year"}
      nil
      {:type "CalculateAgeAt" :operand [{:type "Null"} #elm/date "2018"]
       :precision "Year"}
      nil))

  (testing "Year"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      {:type "CalculateAgeAt" :operand [#elm/date "2018" #elm/date "2019"]
       :precision "Year"}
      1
      {:type "CalculateAgeAt" :operand [#elm/date "2018" #elm/date "2018"]
       :precision "Year"}
      0

      {:type "CalculateAgeAt" :operand [#elm/date "2018" #elm/date "2018"]
       :precision "Month"}
      nil)))
