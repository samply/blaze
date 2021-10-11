(ns blaze.elm.compiler.structured-values-test
  "2. Structured Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.coll.core :as coll]
    [blaze.elm.code-spec]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal]
    [blaze.elm.literal-spec]
    [blaze.fhir.spec.type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [juxt.iota :refer [given]])
  (:import
    [blaze.elm.code Code]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 2.1. Tuple
;;
;; The Tuple expression allows tuples of any type to be built up as an
;; expression. The tupleType attribute specifies the type of the tuple being
;; built, if any, and the list of tuple elements specify the values for the
;; elements of the tuple. Note that the value of an element may be any
;; expression, including another Tuple.
(deftest compile-tuple-test
  (are [elm res] (= res (core/-eval (c/compile {} elm) {} nil nil))
    #elm/tuple{"id" #elm/integer"1"}
    {:id 1}

    #elm/tuple{"id" #elm/integer"1" "name" #elm/string "john"}
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
    (given (c/compile {} (tu/code "system-134534" "code-134551"))
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
                identifier
                #fhir/Identifier
                    {:system #fhir/uri"foo"
                     :value "bar"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :identifier [identifier]}
                expr
                (c/compile
                  {:eval-context "Patient"}
                  elm)
                result (coll/first (core/-eval expr nil nil {"R" entity}))]
            (is (= identifier result))))

        (testing "without source-type"
          (let [elm
                {:path "identifier"
                 :scope "R"
                 :type "Property"}
                identifier
                #fhir/Identifier
                    {:system #fhir/uri"foo"
                     :value "bar"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :identifier [identifier]}
                expr
                (c/compile
                  {:eval-context "Patient"}
                  elm)
                result (coll/first (core/-eval expr nil nil {"R" entity}))]
            (is (= identifier result)))))

      (testing "Patient.extension"
        (testing "without source-type"
          (let [elm
                {:path "extension"
                 :scope "R"
                 :type "Property"}
                extension
                #fhir/Extension
                    {:url "foo"
                     :valueString "bar"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :extension [extension]}
                expr
                (c/compile
                  {:eval-context "Patient"}
                  elm)
                result (coll/first (core/-eval expr nil nil {"R" entity}))]
            (is (= extension result)))))

      (testing "Patient.gender"
        (testing "with source-type"
          (let [elm
                {:path "gender"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Patient"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}
                expr
                (c/compile
                  {:eval-context "Patient"}
                  elm)]
            (is (= #fhir/code"male" (core/-eval expr nil nil {"R" entity})))))

        (testing "without source-type"
          (let [elm
                {:path "gender"
                 :scope "R"
                 :type "Property"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}
                expr
                (c/compile
                  {:eval-context "Patient"}
                  elm)]
            (is (= #fhir/code"male" (core/-eval expr nil nil {"R" entity}))))))

      (testing "Observation.value"
        (testing "with source-type"
          (let [elm
                {:path "value"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Observation"}
                entity
                {:fhir/type :fhir/Observation :id "0"
                 :value "value-114318"}
                expr
                (c/compile
                  {:eval-context "Patient"}
                  elm)]
            (is (= "value-114318" (core/-eval expr nil nil {"R" entity})))))

        (testing "without source-type"
          (let [elm
                {:path "value"
                 :scope "R"
                 :type "Property"}
                entity
                {:fhir/type :fhir/Observation :id "0"
                 :value "value-114318"}
                expr
                (c/compile
                  {:eval-context "Patient"}
                  elm)]
            (is (= "value-114318" (core/-eval expr nil nil {"R" entity})))))))

    (testing "with entity supplied directly"
      (testing "Patient.identifier"
        (testing "with source-type"
          (let [elm
                {:path "identifier"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Patient"}
                identifier
                #fhir/Identifier
                    {:system #fhir/uri"foo"
                     :value "bar"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :identifier [identifier]}
                expr
                (c/compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)
                result (coll/first (core/-eval expr nil nil entity))]
            (is (= identifier result))))

        (testing "without source-type"
          (let [elm
                {:path "identifier"
                 :scope "R"
                 :type "Property"}
                identifier
                #fhir/Identifier
                    {:system #fhir/uri"foo"
                     :value "bar"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :identifier [identifier]}
                expr
                (c/compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)
                result (coll/first (core/-eval expr nil nil entity))]
            (is (= identifier result)))))

      (testing "Patient.gender"
        (testing "with source-type"
          (let [elm
                {:path "gender"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Patient"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}
                expr
                (c/compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)]
            (is (= #fhir/code"male" (core/-eval expr nil nil entity)))))

        (testing "without source-type"
          (let [elm
                {:path "gender"
                 :scope "R"
                 :type "Property"}
                entity
                {:fhir/type :fhir/Patient :id "0"
                 :gender #fhir/code"male"}
                expr
                (c/compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)]
            (is (= #fhir/code"male" (core/-eval expr nil nil entity))))))

      (testing "Observation.value"
        (testing "with source-type"
          (let [elm
                {:path "value"
                 :scope "R"
                 :type "Property"
                 :life/source-type "{http://hl7.org/fhir}Observation"}
                entity
                {:fhir/type :fhir/Observation :id "0"
                 :value "value-114318"}
                expr
                (c/compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)]
            (is (= "value-114318" (core/-eval expr nil nil entity)))))

        (testing "without source-type"
          (let [elm
                {:path "value"
                 :scope "R"
                 :type "Property"}
                entity
                {:fhir/type :fhir/Observation :id "0" :value "value-114318"}
                expr
                (c/compile
                  {:eval-context "Patient"
                   :life/single-query-scope "R"}
                  elm)]
            (is (= "value-114318" (core/-eval expr nil nil entity))))))))

  (testing "with source"
    (testing "Patient.identifier"
      (testing "with source-type"
        (let [library {:statements {:def [{:name "Patient"}]}}
              elm
              {:path "identifier"
               :source #elm/expression-ref "Patient"
               :type "Property"
               :life/source-type "{http://hl7.org/fhir}Patient"}
              identifier
              #fhir/Identifier
                  {:system #fhir/uri"foo"
                   :value "bar"}
              source
              {:fhir/type :fhir/Patient :id "0"
               :identifier [identifier]}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              result (coll/first (core/-eval expr {:library-context {"Patient" source}} nil nil))]
          (is (= identifier result))))

      (testing "without source-type"
        (let [library {:statements {:def [{:name "Patient"}]}}
              elm
              {:path "identifier"
               :source #elm/expression-ref "Patient"
               :type "Property"}
              identifier
              #fhir/Identifier
                  {:system #fhir/uri"foo"
                   :value "bar"}
              source
              {:fhir/type :fhir/Patient :id "0"
               :identifier [identifier]}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              result (coll/first (core/-eval expr {:library-context {"Patient" source}} nil nil))]
          (is (= identifier result)))))

    (testing "Patient.gender"
      (testing "with source-type"
        (let [library {:statements {:def [{:name "Patient"}]}}
              elm
              {:path "gender"
               :source #elm/expression-ref "Patient"
               :type "Property"
               :life/source-type "{http://hl7.org/fhir}Patient"}
              source
              {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              result (core/-eval expr {:library-context {"Patient" source}} nil nil)]
          (is (= #fhir/code"male" result))))

      (testing "without source-type"
        (let [library {:statements {:def [{:name "Patient"}]}}
              elm
              {:path "gender"
               :source #elm/expression-ref "Patient"
               :type "Property"}
              source
              {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code"male"}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              result (core/-eval expr {:library-context {"Patient" source}} nil nil)]
          (is (= #fhir/code"male" result)))))

    (testing "Observation.value"
      (testing "with source-type"
        (let [library {:statements {:def [{:name "Observation"}]}}
              elm
              {:path "value"
               :source #elm/expression-ref "Observation"
               :type "Property"
               :life/source-type "{http://hl7.org/fhir}Observation"}
              source
              {:fhir/type :fhir/Observation :id "0"
               :value "value-114318"}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              result (core/-eval expr {:library-context {"Observation" source}} nil nil)]
          (is (= "value-114318" result))))

      (testing "without source-type"
        (let [library {:statements {:def [{:name "Observation"}]}}
              elm
              {:path "value"
               :source #elm/expression-ref "Observation"
               :type "Property"}
              source
              {:fhir/type :fhir/Observation :id "0"
               :value "value-114318"}
              expr (c/compile {:library library :eval-context "Patient"} elm)
              result (core/-eval expr {:library-context {"Observation" source}} nil nil)]
          (is (= "value-114318" result)))))

    (testing "Tuple"
      (are [elm result]
        (= result (core/-eval (c/compile {:eval-context "Unfiltered"} elm) {} nil nil))
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
          [{:name "id" :value #elm/integer"1"}]}}
        1))

    (testing "Quantity"
      (testing "value"
        (are [elm result]
          (= result (core/-eval (c/compile {:eval-context "Unfiltered"} elm) {} nil nil))
          {:resultTypeName "{urn:hl7-org:elm-types:r1}Decimal"
           :path "value"
           :type "Property"
           :source #elm/quantity[42 "m"]}
          42M))

      (testing "unit"
        (are [elm result]
          (= result (core/-eval (c/compile {:eval-context "Unfiltered"} elm) {} nil nil))
          {:resultTypeName "{urn:hl7-org:elm-types:r1}String"
           :path "unit"
           :type "Property"
           :source #elm/quantity[42 "m"]}
          "m")))

    (testing "nil"
      (are [elm result]
        (= result (core/-eval (c/compile {:eval-context "Unfiltered"} elm) {} nil nil))
        {:path "value"
         :type "Property"
         :source {:type "Null"}}
        nil))))
