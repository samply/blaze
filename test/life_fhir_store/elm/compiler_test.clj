(ns life-fhir-store.elm.compiler-test
  "Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [clojure.test.check]
    [life-fhir-store.datomic.cql :as cql]
    [life-fhir-store.datomic.time :as time]
    [life-fhir-store.datomic.quantity :as quantity]
    [life-fhir-store.elm.compiler
     :refer [compile compile-with-equiv-clause -eval -hash]]
    [life-fhir-store.elm.literals])
  (:import
    [java.time LocalDate LocalDateTime OffsetDateTime Year YearMonth ZoneOffset])
  (:refer-clojure :exclude [compile]))


(defn fixture [f]
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



;; 1. Simple Values

;; 1.1 Literal
(deftest compile-literal-test
  (testing "Boolean Literal"
    (are [elm boolean] (= boolean (compile {} elm))
      #elm/boolean true true
      #elm/boolean false false))

  (testing "Decimal Literal"
    (are [elm decimal] (= decimal (compile {} elm))
      #elm/decimal -1 -1.0
      #elm/decimal -0.1 -0.1
      #elm/decimal 0 0.0
      #elm/decimal 0.1 0.1
      #elm/decimal 1 1.0))

  (testing "Integer Literal"
    (are [elm int] (= int (compile {} elm))
      #elm/integer -1 -1
      #elm/integer 0 0
      #elm/integer 1 1)))



;; 2. Structured Values

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


;; 9. Reusing Logic

;; 9.2. ExpressionRef
(deftest compile-expression-ref-test
  (are [elm result]
    (= result (-eval (compile {} elm) {:library-context {"foo" ::result}} nil))
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
                #elm/integer 2]}
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
       :where #elm/boolean false}
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
  (are [elm code] (= code (-eval (compile {} elm) {} {"foo" ::result}))
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
(deftest compile-equal-test
  (testing "Decimal"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/equal [#elm/decimal 1 #elm/decimal 1] true
      #elm/equal [#elm/decimal 1 #elm/decimal 2] false
      #elm/equal [{:type "Null"} #elm/decimal 1] nil
      #elm/equal [#elm/decimal 1 {:type "Null"}] nil))

  (testing "Integer"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/equal [#elm/integer 1 #elm/integer 1] true
      #elm/equal [#elm/integer 1 #elm/integer 2] false
      #elm/equal [{:type "Null"} #elm/integer 1] nil
      #elm/equal [#elm/integer 1 {:type "Null"}] nil))

  (testing "Quantity"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/equal [#elm/quantity [1] #elm/quantity [1]] true
      #elm/equal [#elm/quantity [1] #elm/quantity [2]] false
      #elm/equal [{:type "Null"} #elm/quantity [1]] nil
      #elm/equal [#elm/quantity [1] {:type "Null"}] nil)))


;; 12.3. Greater
(deftest compile-greater-test
  (testing "Decimal"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/greater [#elm/decimal 1 #elm/decimal 1] false
      #elm/greater [#elm/decimal 2 #elm/decimal 1] true
      #elm/greater [{:type "Null"} #elm/decimal 1] nil
      #elm/greater [#elm/decimal 1 {:type "Null"}] nil))

  (testing "Integer"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/greater [#elm/integer 1 #elm/integer 1] false
      #elm/greater [#elm/integer 2 #elm/integer 1] true
      #elm/greater [{:type "Null"} #elm/integer 1] nil
      #elm/greater [#elm/integer 1 {:type "Null"}] nil))

  (testing "Quantity"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/greater [#elm/quantity [1] #elm/quantity [1]] false
      #elm/greater [#elm/quantity [2] #elm/quantity [1]] true
      #elm/greater [{:type "Null"} #elm/quantity [1]] nil
      #elm/greater [#elm/quantity [2] {:type "Null"}] nil))

  (testing "Date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/greater [#elm/date [2014] #elm/date [2013]] true
      #elm/greater [#elm/date [2014] #elm/date [2015]] false
      #elm/greater [#elm/date [2014] #elm/date [2014]] false
      #elm/greater [#elm/date [2014 1] #elm/date [2014]] nil
      #elm/greater [#elm/date [2014] #elm/date [2014 1]] nil
      #elm/greater [{:type "Null"} #elm/date [2014]] nil
      #elm/greater [#elm/date [2014] {:type "Null"}] nil)))


;; 12.4. GreaterOrEqual
(deftest compile-greater-or-equal-test
  (testing "Decimal"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/greater-or-equal [#elm/decimal 2 #elm/decimal 1] true
      #elm/greater-or-equal [{:type "Null"} #elm/decimal 1] nil
      #elm/greater-or-equal [#elm/decimal 1 {:type "Null"}] nil))

  (testing "Integer"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/greater-or-equal [#elm/integer 1 #elm/integer 1] true
      #elm/greater-or-equal [#elm/integer 2 #elm/integer 1] true
      #elm/greater-or-equal [{:type "Null"} #elm/integer 1] nil
      #elm/greater-or-equal [#elm/integer 1 {:type "Null"}] nil))

  (testing "Quantity"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/greater-or-equal [#elm/quantity [2] #elm/quantity [1]] true
      #elm/greater-or-equal [{:type "Null"} #elm/quantity [1]] nil
      #elm/greater-or-equal [#elm/quantity [1] {:type "Null"}] nil))

  (testing "Date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/greater-or-equal [#elm/date [2014] #elm/date [2013]] true
      #elm/greater-or-equal [#elm/date [2014] #elm/date [2015]] false
      #elm/greater-or-equal [#elm/date [2014] #elm/date [2014]] true
      #elm/greater-or-equal [#elm/date [2014 1] #elm/date [2014]] nil
      #elm/greater-or-equal [#elm/date [2014] #elm/date [2014 1]] nil
      #elm/greater-or-equal [{:type "Null"} #elm/date [2014]] nil
      #elm/greater-or-equal [#elm/date [2014] {:type "Null"}] nil)))


;; 12.5. Less
(deftest compile-less-test
  (testing "Decimal"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/less [#elm/decimal 1 #elm/decimal 1] false
      #elm/less [#elm/decimal 1 #elm/decimal 2] true
      #elm/less [{:type "Null"} #elm/decimal 2] nil
      #elm/less [#elm/decimal 1 {:type "Null"}] nil))

  (testing "Integer"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/less [#elm/integer 1 #elm/integer 1] false
      #elm/less [#elm/integer 1 #elm/integer 2] true))

  (testing "Quantity"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/less [#elm/quantity [1] #elm/quantity [1]] false
      #elm/less [#elm/quantity [1] #elm/quantity [2]] true))

  (testing "Date"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/less [#elm/date [2013 6 14] #elm/date [2013 6 15]] true
      #elm/less [#elm/date [2013 6 16] #elm/date [2013 6 15]] false
      #elm/less [#elm/date [2013 6 15] #elm/date [2013 6 15]] false
      #elm/less [#elm/date [2013 6 15] #elm/date-time [2013 6 15 0]] nil
      #elm/less [#elm/date-time [2013 6 15 0] #elm/date [2013 6 15]] nil))

  (testing "DateTime"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/less [#elm/date-time [2013 6 15 11] #elm/date-time [2013 6 15 12]] true
      #elm/less [#elm/date-time [2013 6 15 13] #elm/date-time [2013 6 15 12]] false
      #elm/less [#elm/date-time [2013 6 15 12] #elm/date-time [2013 6 15 12]] false

      #elm/less [#elm/date-time [2013 6 15 12 0 0 0 0] #elm/date [2013 7]] true)))


;; 12.6. LessOrEqual
(deftest compile-less-or-equal-test
  (testing "Decimal"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/less-or-equal [#elm/decimal 1 #elm/decimal 2] true
      #elm/less-or-equal [{:type "Null"} #elm/decimal 2] nil
      #elm/less-or-equal [#elm/decimal 1 {:type "Null"}] nil))

  (testing "Integer"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/less-or-equal [#elm/integer 1 #elm/integer 1] true
      #elm/less-or-equal [#elm/integer 1 #elm/integer 2] true
      #elm/less-or-equal [{:type "Null"} #elm/integer 2] nil
      #elm/less-or-equal [#elm/integer 1 {:type "Null"}] nil))

  (testing "Quantity"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/less-or-equal [#elm/quantity [1] #elm/quantity [2]] true
      #elm/less-or-equal [{:type "Null"} #elm/quantity [2]] nil
      #elm/less-or-equal [#elm/quantity [1] {:type "Null"}] nil))

  (testing "Date"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/less-or-equal [#elm/date [2013 6 14] #elm/date [2013 6 15]] true
      #elm/less-or-equal [#elm/date [2013 6 16] #elm/date [2013 6 15]] false
      #elm/less-or-equal [#elm/date [2013 6 15] #elm/date [2013 6 15]] true
      #elm/less-or-equal [#elm/date [2013 6 15] #elm/date-time [2013 6 15 0]] nil
      #elm/less-or-equal [#elm/date-time [2013 6 15 0] #elm/date [2013 6 15]] nil)))


;; 12.7. NotEqual
(deftest compile-not-equal-test
  (testing "Decimal"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/not-equal [#elm/decimal 1 #elm/decimal 2] true
      #elm/not-equal [#elm/decimal 1 #elm/decimal 1] false
      #elm/not-equal [{:type "Null"} #elm/decimal 1] nil
      #elm/not-equal [#elm/decimal 1 {:type "Null"}] nil))

  (testing "Integer"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/not-equal [#elm/integer 1 #elm/integer 2] true
      #elm/not-equal [#elm/integer 1 #elm/integer 1] false
      #elm/not-equal [{:type "Null"} #elm/integer 1] nil
      #elm/not-equal [#elm/integer 1 {:type "Null"}] nil))

  (testing "Quantity"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/not-equal [#elm/quantity [1] #elm/quantity [2]] true
      #elm/not-equal [#elm/quantity [1] #elm/quantity [1]] false
      #elm/not-equal [{:type "Null"} #elm/quantity [1]] nil
      #elm/not-equal [#elm/quantity [1] {:type "Null"}] nil)))



;; 13. Logical Operators

;; 13.1. And
(deftest compile-and-test
  (are [a b c] (= c (-eval (compile {} {:type "And" :operand [a b]}) {} nil))
    #elm/boolean true #elm/boolean true true
    #elm/boolean true #elm/boolean false false
    #elm/boolean true {:type "Null"} nil

    #elm/boolean false #elm/boolean true false
    #elm/boolean false #elm/boolean false false
    #elm/boolean false {:type "Null"} false

    {:type "Null"} #elm/boolean true nil
    {:type "Null"} #elm/boolean false false
    {:type "Null"} {:type "Null"} nil))


;; 13.2. Implies
(deftest compile-implies-test
  (is (thrown? Exception (compile {} {:type "Implies"}))))


;; 13.3. Not
(deftest compile-not-test
  (are [a b] (= b (-eval (compile {} {:type "Not" :operand a}) {} nil))
    #elm/boolean true false
    #elm/boolean false true
    {:type "Null"} nil))


;; 13.4. Or
(deftest compile-or-test
  (are [a b c] (= c (-eval (compile {} {:type "Or" :operand [a b]}) {} nil))
    #elm/boolean true #elm/boolean true true
    #elm/boolean true #elm/boolean false true
    #elm/boolean true {:type "Null"} true

    #elm/boolean false #elm/boolean true true
    #elm/boolean false #elm/boolean false false
    #elm/boolean false {:type "Null"} nil

    {:type "Null"} #elm/boolean true true
    {:type "Null"} #elm/boolean false nil
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
    [#elm/boolean false #elm/boolean true] false
    [{:type "Null"} #elm/integer 1 #elm/integer 2] 1
    [#elm/integer 2] 2))


;; 14.3. IsFalse
(deftest compile-is-false-test
  (are [elm res] (= res (-eval (compile {} {:type "IsFalse" :operand elm}) {} nil))
    #elm/boolean true false
    #elm/boolean false true
    {:type "Null"} false))


;; 14.4. IsNull
(deftest compile-is-null-test
  (are [elm res] (= res (-eval (compile {} {:type "IsNull" :operand elm}) {} nil))
    #elm/boolean true false
    #elm/boolean false false
    {:type "Null"} true))


;; 14.5. IsTrue
(deftest compile-is-true-test
  (are [elm res] (= res (-eval (compile {} {:type "IsTrue" :operand elm}) {} nil))
    #elm/boolean true true
    #elm/boolean false false
    {:type "Null"} false))



;; 17. String Operators

;; 17.4. Equal
(deftest compile-equal-string-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    #elm/equal [#elm/string "1" #elm/string "1"] true
    #elm/equal [#elm/string "1" #elm/string "2"] false
    #elm/equal [{:type "Null"} #elm/string "1"] nil
    #elm/equal [#elm/string "1" {:type "Null"}] nil))


;; 17.11. Not Equal
(deftest compile-not-equal-string-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    #elm/not-equal [#elm/string "1" #elm/string "2"] true
    #elm/not-equal [#elm/string "1" #elm/string "1"] false
    #elm/not-equal [{:type "Null"} #elm/string "1"] nil
    #elm/not-equal [#elm/string "1" {:type "Null"}] nil))



;; 16. Arithmetic Operators

;; 16.1. Abs
(deftest compile-abs-test
  (are [elm res] (= res (-eval (compile {} {:type "Abs" :operand elm}) {} nil))
    #elm/integer -1 1
    #elm/integer 0 0
    #elm/integer 1 1

    #elm/decimal -1 1.0
    #elm/decimal 0 0.0
    #elm/decimal 1 1.0

    #elm/quantity [-1] 1
    #elm/quantity [0] 0
    #elm/quantity [1] 1))



;; 18. Date and Time Operators

;; 18.4. Equal
(deftest compile-equal-date-time-test
  (testing "date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/equal [#elm/date [2012] #elm/date [2012]] true
      #elm/equal [#elm/date [2012] #elm/date [2013]] false
      #elm/equal [{:type "Null"} #elm/date [2012]] nil
      #elm/equal [#elm/date [2012] {:type "Null"}] nil)))


;; 18.6. Date
(deftest compile-date-test
  (testing "year"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date [2019]
      (Year/of 2019)))

  (testing "year-month"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date [2019 3]
      (YearMonth/of 2019 3)))

  (testing "date"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date [2019 3 23]
      (LocalDate/of 2019 3 23))))


;; 18.8. DateTime
(deftest compile-date-time-test
  (testing "year"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date-time [2019]
      (Year/of 2019)))

  (testing "year-month"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date-time [2019 3]
      (YearMonth/of 2019 3)))

  (testing "date"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date-time [2019 3 23]
      (LocalDate/of 2019 3 23)))

  (testing "hour"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date-time [2019 3 23 12]
      (LocalDateTime/of 2019 3 23 12 0 0)))

  (testing "minute"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date-time [2019 3 23 12 13]
      (LocalDateTime/of 2019 3 23 12 13 0)))

  (testing "second"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date-time [2019 3 23 12 13 14]
      (LocalDateTime/of 2019 3 23 12 13 14)))

  (testing "with offset"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date-time [2019 3 23 12 13 14 0 -2]
      (LocalDateTime/of 2019 3 23 14 13 14)

      #elm/date-time [2019 3 23 12 13 14 0 -1]
      (LocalDateTime/of 2019 3 23 13 13 14)

      #elm/date-time [2019 3 23 12 13 14 0 0]
      (LocalDateTime/of 2019 3 23 12 13 14)

      #elm/date-time [2019 3 23 12 13 14 0 1]
      (LocalDateTime/of 2019 3 23 11 13 14)

      #elm/date-time [2019 3 23 12 13 14 0 2]
      (LocalDateTime/of 2019 3 23 10 13 14)))

  (testing "with decimal offset"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      #elm/date-time [2019 3 23 12 13 14 0 1.5]
      (LocalDateTime/of 2019 3 23 10 43 14))))


;; 18.11. DurationBetween
(deftest compile-duration-between-test
  (testing "Year"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      {:type "DurationBetween" :operand [#elm/date [2018] #elm/date [2019]]
       :precision "Year"}
      1
      {:type "DurationBetween" :operand [#elm/date [2018] #elm/date [2017]]
       :precision "Year"}
      -1
      {:type "DurationBetween" :operand [#elm/date [2018] #elm/date [2018]]
       :precision "Year"}
      0

      {:type "DurationBetween" :operand [#elm/date [2018] #elm/date [2018]]
       :precision "Month"}
      nil))

  (testing "Month"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      {:type "DurationBetween" :operand [#elm/date [2018 1] #elm/date [2018 2]]
       :precision "Month"}
      1
      {:type "DurationBetween" :operand [#elm/date [2018 1] #elm/date [2017 12]]
       :precision "Month"}
      -1
      {:type "DurationBetween" :operand [#elm/date [2018 1] #elm/date [2018 1]]
       :precision "Month"}
      0

      {:type "DurationBetween" :operand [#elm/date [2018 1] #elm/date [2018 1]]
       :precision "Day"}
      nil))

  (testing "Day"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      {:type "DurationBetween" :operand [#elm/date [2018 1 1] #elm/date [2018 1 2]]
       :precision "Day"}
      1
      {:type "DurationBetween" :operand [#elm/date [2018 1 1] #elm/date [2017 12 31]]
       :precision "Day"}
      -1
      {:type "DurationBetween" :operand [#elm/date [2018 1 1] #elm/date [2018 1 1]]
       :precision "Day"}
      0

      {:type "DurationBetween" :operand [#elm/date [2018 1 1] #elm/date [2018 1 1]]
       :precision "Hour"}
      nil))

  (testing "Hour"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      {:type "DurationBetween" :operand [#elm/date-time [2018 1 1 0] #elm/date-time [2018 1 1 1]]
       :precision "Hour"}
      1
      {:type "DurationBetween" :operand [#elm/date-time [2018 1 1 0] #elm/date-time [2017 12 31 23]]
       :precision "Hour"}
      -1
      {:type "DurationBetween" :operand [#elm/date-time [2018 1 1 0] #elm/date-time [2018 1 1 0]]
       :precision "Hour"}
      0

      ;; TODO: this is not right according the spec. We don't have the hour and minute precision
      {:type "DurationBetween" :operand [#elm/date-time [2018 1 1 0] #elm/date-time [2018 1 1 0]]
       :precision "Minute"}
      0)))


;; 18.12. Not Equal
(deftest compile-not-equal-date-time-test
  (testing "date"
    (are [elm res] (= res (-eval (compile {} elm) {} nil))
      #elm/not-equal [#elm/date [2012] #elm/date [2013]] true
      #elm/not-equal [#elm/date [2012] #elm/date [2012]] false
      #elm/not-equal [{:type "Null"} #elm/date [2012]] nil
      #elm/not-equal [#elm/date [2012] {:type "Null"}] nil)))


;; 18.13. Now
(deftest compile-now-test
  (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
    {:type "Now"}
    now))


;; 18.22. Today
(deftest compile-today-test
  (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
    {:type "Today"}
    (.toLocalDate now)))



;; 19. Interval Operators

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
     :operand [#elm/list [#elm/integer 1]]}
    #{1}

    {:type "Intersect"
     :operand
     [#elm/list [#elm/integer 1]
      #elm/list [#elm/integer 1 #elm/integer 2]]}
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
     :operand [#elm/list [#elm/integer 1]]}
    #{1}

    {:type "Union"
     :operand
     [#elm/list [#elm/integer 1]
      #elm/list [#elm/integer 2]]}
    #{1 2}

    {:type "Union"
     :operand
     [#elm/list [#elm/integer 1]
      #elm/list [#elm/integer 1 #elm/integer 2]]}
    #{1 2}))



;; 20. List Operators

;; 20.1. List
(deftest compile-list-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    #elm/list []
    []

    #elm/list [{:type "Null"}]
    [nil]

    #elm/list [#elm/integer 1]
    [1]

    #elm/list [#elm/integer 1 {:type "Null"}]
    [1 nil]

    #elm/list [#elm/integer 1 #elm/integer 2]
    [1 2]))


;; 20.5. Equal
(deftest compile-equal-list-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    #elm/equal [#elm/list [#elm/integer 1] #elm/list [#elm/integer 1]] true
    #elm/equal [#elm/list [#elm/integer 1] #elm/list []] false
    #elm/equal [#elm/list [#elm/integer 1] #elm/list [#elm/integer 2]] false
    #elm/equal [{:type "Null"} #elm/list [#elm/integer 1]] nil
    #elm/equal [#elm/list [#elm/integer 1] {:type "Null"}] nil))


;; 20.19. Not Equal
(deftest compile-not-equal-list-test
  (are [elm res] (= res (-eval (compile {} elm) {} nil))
    #elm/not-equal [#elm/list [#elm/integer 1] #elm/list []] true
    #elm/not-equal [#elm/list [#elm/integer 1] #elm/list [#elm/integer 2]] true
    #elm/not-equal [#elm/list [#elm/integer 1] #elm/list [#elm/integer 1]] false
    #elm/not-equal [{:type "Null"} #elm/list [#elm/integer 1]] nil
    #elm/not-equal [#elm/list [#elm/integer 1] {:type "Null"}] nil))


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
     :operand #elm/list [#elm/integer 1]}
    1)

  (let [elm {:type "SingletonFrom"
             :operand #elm/list [#elm/integer 1 #elm/integer 1]}
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
     :source #elm/list [#elm/integer 1]}
    1

    {:type "Count"
     :source #elm/list [#elm/integer 1 #elm/integer 1]}
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

    {:type "ToDate" :operand #elm/date [2019]}
    (Year/of 2019)

    {:type "ToDate" :operand #elm/date-time [2019 1 1 12 13 14]}
    (LocalDate/of 2019 1 1)))


;; 22.20. ToDateTime
(deftest compile-to-date-time-test
  (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
    {:type "ToDateTime" :operand #elm/date [2019]}
    (Year/of 2019)))


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

    {:type "ToList" :operand #elm/boolean false}
    [false]

    {:type "ToList" :operand #elm/integer 1}
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
      {:type "CalculateAgeAt" :operand [#elm/date [2018] {:type "Null"}]
       :precision "Year"}
      nil
      {:type "CalculateAgeAt" :operand [{:type "Null"} #elm/date [2018]]
       :precision "Year"}
      nil))

  (testing "Year"
    (are [elm res] (= res (-eval (compile {} elm) {:now now} nil))
      {:type "CalculateAgeAt" :operand [#elm/date [2018] #elm/date [2019]]
       :precision "Year"}
      1
      {:type "CalculateAgeAt" :operand [#elm/date [2018] #elm/date [2018]]
       :precision "Year"}
      0

      {:type "CalculateAgeAt" :operand [#elm/date [2018] #elm/date [2018]]
       :precision "Month"}
      nil)))
