(ns blaze.fhir.spec-test
  "Terms:
   * parse - bytes to internal format
   * write - internal format to bytes
   * read  - bytes to generic Clojure data types"
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec-spec]
   [blaze.fhir.spec.generators :as fg]
   [blaze.fhir.spec.impl.xml-spec]
   [blaze.fhir.spec.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.util :as fu]
   [blaze.fhir.writing-context]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.alpha.spec :as s2]
   [clojure.data.xml :as xml]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.prxml :as prxml]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [juxt.iota :refer [given]])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [java.time Instant]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")
(xml-name/alias-uri 'xhtml "http://www.w3.org/1999/xhtml")

(st/instrument)
(set! *warn-on-reflection* true)

(test/use-fixtures :each tu/fixture)

(def ^:private cbor-factory
  (-> (CBORFactory/builder)
      (.build)))

(def ^:private cbor-object-mapper
  (j/object-mapper {:factory cbor-factory :encode-key-fn true :decode-key-fn true}))

(defn- read-json [source]
  (j/read-value source j/keyword-keys-object-mapper))

(defn- read-cbor [source]
  (j/read-value source cbor-object-mapper))

(def ^:private parsing-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}))

(def ^:private rs-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo
    :fail-on-unknown-property false
    :include-summary-only true
    :use-regex false}))

(defn- parse-json
  ([source]
   (fhir-spec/parse-json parsing-context source))
  ([type source]
   (fhir-spec/parse-json parsing-context type source)))

(defn- parse-cbor
  ([type source]
   (fhir-spec/parse-cbor rs-context type source))
  ([type source variant]
   (fhir-spec/parse-cbor rs-context type source variant)))

(def ^:private writing-context
  (ig/init-key
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}))

(defn- write-json [x]
  (fhir-spec/write-json-as-bytes writing-context x))

(defn- write-cbor [x]
  (fhir-spec/write-cbor writing-context x))

(defn- write-read-json [x]
  (read-json (fhir-spec/write-json-as-string writing-context x)))

(defn- write-parse-json
  ([data]
   (fhir-spec/parse-json parsing-context (j/write-value-as-string data)))
  ([type data]
   (fhir-spec/parse-json parsing-context type (j/write-value-as-string data))))

(defn- write-parse-cbor
  ([{:fhir/keys [type] :as resource}]
   (parse-cbor (name type) (write-cbor resource)))
  ([type data]
   (fhir-spec/parse-cbor rs-context type (j/write-value-as-bytes data cbor-object-mapper))))

(deftest resource-test
  (testing "valid"
    (are [x] (s2/valid? :fhir/Resource x)
      {:fhir/type :fhir/Condition :id "id-204446"
       :meta
       #fhir/Meta
        {:versionId #fhir/id"1"
         :profile [#fhir/canonical"url-164445"]}
       :code
       #fhir/CodeableConcept
        {:coding
         [#fhir/Coding
           {:system #fhir/uri"system-204435"
            :code #fhir/code"code-204441"}]}
       :subject #fhir/Reference{:reference "Patient/id-145552"}
       :onset #fhir/dateTime"2020-01-30"}

      {:fhir/type :fhir/Patient :id "0"
       :birthDate #fhir/date{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}})))

(deftest fhir-type-test
  (testing "Patient"
    (is (= :fhir/Patient
           (fhir-spec/fhir-type
            (write-parse-json {:resourceType "Patient"}))))))

(deftest primitive-test
  (are [spec] (fhir-spec/primitive? spec)
    :fhir/id)

  (are [spec] (not (fhir-spec/primitive? spec))
    :fhir/Patient
    nil
    "foo"
    :Patient))

(deftest primitive-val-test
  (are [x] (fhir-spec/primitive-val? x)
    "foo"
    1
    #fhir/code"bar")

  (are [x] (not (fhir-spec/primitive-val? x))
    #fhir/Coding{}
    {}))

(deftest resource-id-test
  (are [s] (s/valid? :blaze.resource/id s)
    "."
    "-"
    "a"
    "A"
    "0"))

(deftest patient-id-test
  (are [s] (s2/valid? :fhir.Patient/id s)
    "."
    "-"
    "a"
    "A"
    "0"))

(deftest type-exists-test
  (testing "true"
    (are [type] (true? (fhir-spec/type-exists? type))
      "Patient"
      "Observation"))

  (testing "false"
    (are [type] (false? (fhir-spec/type-exists? type))
      "Foo")))

(deftest fhir-path-test
  (testing "key and number in vector"
    (let [result (fhir-spec/fhir-path [:contact 2] {:resourceType "Patient"
                                                    :contact [2]})]
      (is (= result "contact[0]"))))

  (testing "key and number in vector"
    (let [result (fhir-spec/fhir-path [:contact 2] {:resourceType "Patient"
                                                    :contact [{} 2 {}]})]
      (is (= result "contact[1]"))))

  (testing "keys and map in vector"
    (let [result (fhir-spec/fhir-path [:name {:text []} :text] {:resourceType "Patient"
                                                                :name [{:text []}]})]
      (is (= result "name[0].text")))))

(deftest parse-json-test
  (testing "fails on unexpected end-of-input"
    (given (parse-json "{")
      ::anom/category := ::anom/incorrect
      ::anom/message :# "Unexpected end-of-input: expected close marker for Object(.|\\s)*")

    (given (parse-json "Patient" "{\"id")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input while parsing a field name."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input while parsing a field name."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient")

    (given (parse-json "Patient" "{\"id\"")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient")

    (given (parse-json "Patient" "{\"id\":\"")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input while reading a string value."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input while reading a string value."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.id")

    (given (parse-json "Patient" "{\"active\":t")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. JSON parsing error."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "JSON parsing error."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient")

    (given (parse-json "Patient" "{\"active\":1")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `boolean`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `boolean`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.active"))

  (testing "fails on trailing token"
    (given (parse-json "Patient" "{}{")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. incorrect trailing token START_OBJECT"))

  (testing "nil"
    (given (write-parse-json nil)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value null. Expected type is `Resource`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value null. Expected type is `Resource`."
      [:fhir/issues 0 :fhir.issues/expression] := ""))

  (testing "string"
    (given (write-parse-json "foo")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `Resource`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `Resource`."
      [:fhir/issues 0 :fhir.issues/expression] := "")

    (testing "type Patient"
      (given (write-parse-json "Patient" "foo")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `Patient`."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `Patient`."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient")))

  (testing "empty map"
    (given (write-parse-json {})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Missing property `resourceType`."))

  (testing "different resource type"
    (given (write-parse-json "Patient" {:resourceType "Observation"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Incorrect resource type `Observation`. Expected type is `Patient`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Incorrect resource type `Observation`. Expected type is `Patient`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient"))

  (testing "invalid Patient.id"
    (given (write-parse-json {:resourceType "Patient" :id 0})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 0. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.id")

    (given (write-parse-json {:resourceType "Patient" :id 1.0})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on float value 1.0. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on float value 1.0. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.id"))

  (testing "invalid Patient.active"
    (given (write-parse-json {:resourceType "Patient" :active "true"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `true`. Expected type is `boolean`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `true`. Expected type is `boolean`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.active"))

  (testing "invalid Patient.name"
    (given (write-parse-json {:resourceType "Patient" :name "John"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `John`. Expected type is `HumanName`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `John`. Expected type is `HumanName`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.name"))

  (testing "invalid Patient.name.family"
    (given (write-parse-json {:resourceType "Patient" :name {:family 0}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 0. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.name[0].family")

    (given (write-parse-json {:resourceType "Patient" :name {:family true}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on boolean value true. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on boolean value true. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.name[0].family")

    (given (write-parse-json {:resourceType "Patient" :name {:family false}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on boolean value false. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on boolean value false. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.name[0].family")

    (given (write-parse-json {:resourceType "Patient" :name {:family []}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on array start. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on array start. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.name[0].family")

    (given (write-parse-json {:resourceType "Patient" :name {:family {}}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on object start. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on object start. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.name[0].family"))

  (testing "invalid Patient.name.given"
    (given (write-parse-json {:resourceType "Patient" :name {:given 0}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string[]`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 0. Expected type is `string[]`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.name[0].given"))

  (testing "invalid Patient.gender"
    (given (write-parse-json "Patient" {:resourceType "Patient" :gender "a  b"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `a  b`. Expected type is `code, regex [\\u0021-\\uFFFF]+([ \\t\\n\\r][\\u0021-\\uFFFF]+)*`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `a  b`. Expected type is `code, regex [\\u0021-\\uFFFF]+([ \\t\\n\\r][\\u0021-\\uFFFF]+)*`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender"))

  (testing "unexpected end of input"
    (testing "within field name"
      (given (parse-json "Patient" "{\"gender")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input while parsing a field name."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input while parsing a field name."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient"))

    (testing "between field name and value"
      (given (parse-json "Patient" "{\"gender\"")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient"))

    (testing "within string value"
      (given (parse-json "Patient" "{\"gender\":\"")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input while reading a string value."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input while reading a string value."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender"))

    (testing "within boolean value"
      (given (parse-json "Patient" "{\"active\":t")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. JSON parsing error."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "JSON parsing error."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient"))

    (testing "after boolean value"
      (given (parse-json "Patient" "{\"active\":true")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient"))

    (testing "after integer value"
      (given (parse-json "Observation" "{\"valueInteger\":0")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/expression] := "Observation"))

    (testing "within an array"
      (given (parse-json "Patient" "{\"name\":[")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient.name"))

    (testing "within an object in an array"
      (given (parse-json "Patient" "{\"name\":[{")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Unexpected end of input."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient.name[0]")))

  (testing "invalid Patient.gender.id"
    (given (write-parse-json {:resourceType "Patient" :_gender {:id 0}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 0. Expected type is `string`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.gender.id"))

  (testing "invalid Patient.gender.extension.url"
    (doseq [[value idx] [[{:extension {:url 0}} 0]
                         [{:extension [{:url 0}]} 0]
                         [{:extension [{:url "foo"} {:url 0}]} 1]]]
      (given (write-parse-json {:resourceType "Patient" :_gender value})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `uri`."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 0. Expected type is `uri`."
        [:fhir/issues 0 :fhir.issues/expression] := (format "Patient.gender.extension[%d].url" idx))))

  (testing "unknown property Patient.gender.extension.name-163857"
    (doseq [[value idx] [[{:extension {:name-163857 "foo"}} 0]
                         [{:extension [{:name-163857 "foo"}]} 0]
                         [{:extension [{:url "foo"} {:name-163857 "foo"}]} 1]]]
      (given (write-parse-json {:resourceType "Patient" :_gender value})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Unknown property `name-163857`."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Unknown property `name-163857`."
        [:fhir/issues 0 :fhir.issues/expression] := (format "Patient.gender.extension[%d]" idx))))

  (testing "invalid Patient.birthDate"
    (testing "invalid token"
      (given (write-parse-json {:resourceType "Patient" :birthDate 0})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `date`."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 0. Expected type is `date`."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient.birthDate"))

    (testing "invalid string"
      (given (write-parse-json {:resourceType "Patient" :birthDate "a"})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `date`."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `a`. Expected type is `date`."
        [:fhir/issues 0 :fhir.issues/expression] := "Patient.birthDate")))

  (testing "invalid Patient.deceasedBoolean"
    (given (write-parse-json {:resourceType "Patient" :deceasedBoolean "a"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `boolean`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `a`. Expected type is `boolean`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.deceased"))

  (testing "invalid Patient.maritalStatus"
    (given (write-parse-json {:resourceType "Patient" :maritalStatus "a"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `CodeableConcept`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `a`. Expected type is `CodeableConcept`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.maritalStatus"))

  (testing "invalid Patient.contact"
    (given (write-parse-json {:resourceType "Patient" :contact "a"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `BackboneElement`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `a`. Expected type is `BackboneElement`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.contact"))

  (testing "invalid Patient.contact.organization"
    (given (write-parse-json {:resourceType "Patient" :contact {:organization "a"}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `Reference`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `a`. Expected type is `Reference`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient.contact[0].organization"))

  (testing "Bundle resource: nil"
    (given (write-parse-json
            {:resourceType "Bundle"
             :type "transaction"
             :entry [{:resource nil}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value null. Expected type is `Resource`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value null. Expected type is `Resource`."
      [:fhir/issues 0 :fhir.issues/expression] := "Bundle.entry[0].resource"))

  (testing "Bundle resource: string"
    (given (write-parse-json
            {:resourceType "Bundle"
             :type "transaction"
             :entry [{:resource "foo"}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `Resource`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `Resource`."
      [:fhir/issues 0 :fhir.issues/expression] := "Bundle.entry[0].resource"))

  (testing "Bundle resource: Patient.gender error"
    (given (write-parse-json
            {:resourceType "Bundle"
             :type "transaction"
             :entry [{:resource {:resourceType "Patient" :gender 0}}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `code`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 0. Expected type is `code`."
      [:fhir/issues 0 :fhir.issues/expression] := "Bundle.entry[0].resource.gender"))

  (testing "Bundle resource: empty map"
    (given (write-parse-json
            {:resourceType "Bundle"
             :type "transaction"
             :entry [{:resource {}}]})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Missing property `resourceType`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Missing property `resourceType`."
      [:fhir/issues 0 :fhir.issues/expression] := "Bundle.entry[0].resource"))

  (testing "invalid Observation.value.value"
    (given (write-parse-json {:resourceType "Observation" :valueQuantity {:value "a"}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `decimal`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `a`. Expected type is `decimal`."
      [:fhir/issues 0 :fhir.issues/expression] := "Observation.value.value"))

  (testing "Observation with invalid control character in value"
    (given (write-parse-json
            {:resourceType "Observation"
             :valueString "foo\u001Ebar"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value `foo\u001Ebar`. Expected type is `string, regex [\\r\\n\\t\\u0020-\\uFFFF]+`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo\u001Ebar`. Expected type is `string, regex [\\r\\n\\t\\u0020-\\uFFFF]+`."
      [:fhir/issues 0 :fhir.issues/expression] := "Observation.value"))

  (testing "empty patient resource"
    (testing "gets type annotated"
      (is (= :fhir/Patient
             (fhir-spec/fhir-type (write-parse-json {:resourceType "Patient"})))))

    (testing "stays the same"
      (is (= {:fhir/type :fhir/Patient}
             (write-parse-json {:resourceType "Patient"})))))

  (testing "deceasedBoolean on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased true}
           (write-parse-json {:resourceType "Patient" :deceasedBoolean true}))))

  (testing "deceasedDateTime on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased #fhir/dateTime"2020"}
           (write-parse-json {:resourceType "Patient" :deceasedDateTime "2020"}))))

  (testing "multipleBirthInteger on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :multipleBirth 2}
           (write-parse-json {:resourceType "Patient" :multipleBirthInteger 2}))))

  (testing "with unknown property"
    (given (write-parse-json {:resourceType "Patient" :unknown "foo"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Unknown property `unknown`."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Unknown property `unknown`."
      [:fhir/issues 0 :fhir.issues/expression] := "Patient"))

  (testing "Patient with id"
    (given (write-parse-json {:resourceType "Patient" :id "id-220105"})
      :fhir/type := :fhir/Patient
      :id := "id-220105"))

  (testing "Observation with code"
    (is (= {:fhir/type :fhir/Observation
            :code
            #fhir/CodeableConcept
             {:coding
              [#fhir/Coding
                {:system #fhir/uri"http://loinc.org"
                 :code #fhir/code"39156-5"}]}}
           (write-parse-json
            {:resourceType "Observation"
             :code {:coding [{:system "http://loinc.org" :code "39156-5"}]}}))))

  (testing "Observation with valueTime"
    (is (= {:fhir/type :fhir/Observation
            :value #fhir/time"16:26:42"}
           (write-parse-json
            {:resourceType "Observation"
             :valueTime "16:26:42"}))))

  (testing "Observation with valueTime with id"
    (is (= {:fhir/type :fhir/Observation
            :value #fhir/time{:id "foo" :value #system/time"16:26:42"}}
           (write-parse-json
            {:resourceType "Observation"
             :valueTime "16:26:42"
             :_valueTime {:id "foo"}}))))

  (testing "Observation with valueTime with extension"
    (is (= {:fhir/type :fhir/Observation
            :value #fhir/time{:extension [#fhir/Extension{:url "foo"}] :value #system/time"16:26:42"}}
           (write-parse-json
            {:resourceType "Observation"
             :valueTime "16:26:42"
             :_valueTime {:extension {:url "foo"}}}))))

  (testing "questionnaire resource with item groups"
    (is (= {:fhir/type :fhir/Questionnaire
            :item
            [{:fhir/type :fhir.Questionnaire/item
              :type #fhir/code"group"
              :item
              [{:fhir/type :fhir.Questionnaire/item
                :type #fhir/code"string"
                :text "foo"}]}]}
           (write-parse-json
            {:resourceType "Questionnaire"
             :item
             [{:type "group"
               :item
               [{:type "string"
                 :text "foo"}]}]})))))

(deftest write-json-test
  (testing "without fhir type"
    (testing "at the root"
      (is (= 0 (alength ^bytes (write-json {})))))

    (testing "backbone elements don't need types"
      (testing "cardinality single"
        (testing "no keys"
          (are [resource json] (= json (write-read-json resource))
            {:fhir/type :fhir.Bundle/entry :search {}}
            {:search {}}))

        (testing "one unknown key"
          (are [resource json] (= json (write-read-json resource))
            {:fhir/type :fhir/Bundle
             :entry [{:fhir/type :fhir.Bundle/entry :request {:foo "bar"}}]}
            {:resourceType "Bundle"
             :entry [{:request {}}]}))

        (testing "one known key"
          (are [resource json] (= json (write-read-json resource))
            {:fhir/type :fhir/Bundle
             :entry [{:fhir/type :fhir.Bundle/entry :request {:url #fhir/uri"bar"}}]}
            {:resourceType "Bundle"
             :entry [{:request {:url "bar"}}]})))

      (testing "cardinality many"
        (are [resource json] (= json (write-read-json resource))
          {:fhir/type :fhir/Bundle :entry [{}]}
          {:resourceType "Bundle" :entry [{}]})))

    (testing "elements don't need types"
      (testing "DataRequirement.codeFilter"
        (are [resource json] (= json (write-read-json resource))
          {:fhir/type :fhir/DataRequirement
           :codeFilter [{:searchParam #fhir/string"bar" :path #fhir/string"foo"}]}
          {:codeFilter [{:path "foo" :searchParam "bar"}]}))))

  (testing "Patient with deceasedBoolean"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Patient :deceased true}
      {:resourceType "Patient" :deceasedBoolean true}))

  (testing "Patient with deceasedDateTime"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Patient :deceased #fhir/dateTime"2020"}
      {:resourceType "Patient" :deceasedDateTime "2020"}))

  (testing "Patient with multipleBirthBoolean"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Patient :multipleBirth false}
      {:resourceType "Patient" :multipleBirthBoolean false}))

  (testing "Patient with multipleBirthInteger"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Patient :multipleBirth (int 2)}
      {:resourceType "Patient" :multipleBirthInteger 2}))

  (testing "Bundle with Patient"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Bundle
       :entry
       [{:fhir/type :fhir.Bundle/entry
         :resource {:fhir/type :fhir/Patient :id "0"}}]}
      {:resourceType "Bundle"
       :entry
       [{:resource {:resourceType "Patient" :id "0"}}]}))

  (testing "Observation with code"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Observation
       :code
       #fhir/CodeableConcept
        {:coding
         [#fhir/Coding
           {:system #fhir/uri"http://loinc.org"
            :code #fhir/code"39156-5"}]}}
      {:resourceType "Observation"
       :code
       {:coding
        [{:system "http://loinc.org"
          :code "39156-5"}]}}))

  (testing "Observation with valueQuantity"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Observation
       :value
       #fhir/Quantity
        {:value 36.6M
         :unit #fhir/string"kg/m^2"
         :system #fhir/uri"http://unitsofmeasure.org"
         :code #fhir/code"kg/m2"}}
      {:resourceType "Observation"
       :valueQuantity
       {:value 36.6
        :unit "kg/m^2"
        :system "http://unitsofmeasure.org"
        :code "kg/m2"}})))

(deftest parse-cbor-test
  (testing "fails on empty input"
    (given (parse-cbor "Patient" (byte-array 0))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on token null. Expected type is `Patient`."))

  (testing "nil"
    (given (write-parse-cbor "Patient" nil)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on value null. Expected type is `Patient`."
      :x := nil))

  (testing "Patient"
    (testing "without properties"
      (is (= (write-parse-cbor "Patient" {:resourceType "Patient"})
             {:fhir/type :fhir/Patient})))

    (testing "with one unknown property"
      (testing "string"
        (is (= (write-parse-cbor "Patient" {:resourceType "Patient" :unknown "foo"})
               {:fhir/type :fhir/Patient})))

      (testing "object"
        (is (= (write-parse-cbor "Patient" {:resourceType "Patient" :unknown {}})
               {:fhir/type :fhir/Patient}))

        (testing "with children"
          (is (= (write-parse-cbor "Patient" {:resourceType "Patient" :unknown {:foo "bar"}})
                 {:fhir/type :fhir/Patient}))))

      (testing "array"
        (is (= (write-parse-cbor "Patient" {:resourceType "Patient" :unknown []})
               {:fhir/type :fhir/Patient}))

        (testing "with children"
          (is (= (write-parse-cbor "Patient" {:resourceType "Patient" :unknown ["foo" "bar"]})
                 {:fhir/type :fhir/Patient})))))))

(deftest write-cbor-test
  (testing "Patient with deceasedBoolean"
    (are [resource] (= resource (write-parse-cbor resource))
      {:fhir/type :fhir/Patient :deceased true}))

  (testing "Patient with deceasedDateTime"
    (are [resource] (= resource (write-parse-cbor resource))
      {:fhir/type :fhir/Patient :deceased #fhir/dateTime"2020"}))

  (testing "Patient with multipleBirthBoolean"
    (are [resource] (= resource (write-parse-cbor resource))
      {:fhir/type :fhir/Patient :multipleBirth false}))

  (testing "Patient with multipleBirthInteger"
    (are [resource] (= resource (write-parse-cbor resource))
      {:fhir/type :fhir/Patient :multipleBirth (int 2)}))

  (testing "Bundle with Patient"
    (are [resource] (= resource (write-parse-cbor resource))
      {:fhir/type :fhir/Bundle
       :entry
       [{:fhir/type :fhir.Bundle/entry
         :resource {:fhir/type :fhir/Patient :id "0"}}]}))

  (testing "Observation with code"
    (are [resource] (= resource (write-parse-cbor resource))
      {:fhir/type :fhir/Observation
       :code
       #fhir/CodeableConcept
        {:coding
         [#fhir/Coding
           {:system #fhir/uri"http://loinc.org"
            :code #fhir/code"39156-5"}]}}))

  (testing "Observation with valueQuantity"
    (are [resource] (= resource (write-parse-cbor resource))
      {:fhir/type :fhir/Observation
       :value
       #fhir/Quantity
        {:value 36.6M
         :unit #fhir/string"kg/m^2"
         :system #fhir/uri"http://unitsofmeasure.org"
         :code #fhir/code"kg/m2"}}))

  (testing "Observation with valueTime"
    (are [resource] (= resource (write-parse-cbor resource))
      {:fhir/type :fhir/Observation
       :value #fhir/time"00:00"}))

  (testing "Observation with valueTime with id"
    (are [resource] (= resource (write-parse-cbor resource))
      {:fhir/type :fhir/Observation
       :value #fhir/time{:id "foo" :value #system/time"00:00"}})))

(defn- conform-xml [sexp]
  (fhir-spec/conform-xml (prxml/sexp-as-element sexp)))

(def ^:private sexp prxml/sexp-as-element)

(defn- sexp-value [value]
  (sexp [nil {:value value}]))

(deftest conform-xml-test
  (testing "nil"
    (given (conform-xml nil)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid XML representation of a resource."
      [:fhir/issues 0 :fhir.issues/code] := "value"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Invalid resource element `null`."))

  (testing "string"
    (given (conform-xml "foo")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid XML representation of a resource."
      [:fhir/issues 0 :fhir.issues/code] := "value"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Invalid resource element `foo`."))

  (testing "Bundle resource: nil"
    (given (conform-xml
            [::f/Bundle {:xmlns "http://hl7.org/fhir"}
             [::f/entry
              [::f/resource]]])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid XML representation of a resource."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `<:xmlns.http%3A%2F%2Fhl7.org%2Ffhir/resource/>`. Expected type is `Resource`."))

  (testing "Bundle resource: string"
    (given (conform-xml
            [::f/Bundle {:xmlns "http://hl7.org/fhir"}
             [::f/entry
              [::f/resource "foo"]]])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid XML representation of a resource."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `<:xmlns.http%3A%2F%2Fhl7.org%2Ffhir/resource>foo</:xmlns.http%3A%2F%2Fhl7.org%2Ffhir/resource>`. Expected type is `Resource`."))

  (testing "Observation with invalid control character in value"
    (given (conform-xml
            [::f/Observation {:xmlns "http://hl7.org/fhir"}
             [::f/valueString {:value "foo\u001Ebar"}]])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid XML representation of a resource."
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo\u001Ebar`. Expected type is `string`, regex `[\\r\\n\\t\\u0020-\\uFFFF]+`."))

  (testing "empty patient resource"
    (testing "gets type annotated"
      (is (= :fhir/Patient
             (fhir-spec/fhir-type (conform-xml [::f/Patient])))))

    (testing "stays the same"
      (is (= {:fhir/type :fhir/Patient}
             (conform-xml [::f/Patient])))))

  (testing "patient resource with id"
    (is (= {:fhir/type :fhir/Patient :id "0"}
           (conform-xml [::f/Patient [::f/id {:value "0"}]]))))

  (testing "deceasedBoolean on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased true}
           (conform-xml [::f/Patient [::f/deceasedBoolean {:value "true"}]]))))

  (testing "deceasedDateTime on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased #fhir/dateTime"2020"}
           (conform-xml [::f/Patient [::f/deceasedDateTime {:value "2020"}]]))))

  (testing "multipleBirthInteger on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :multipleBirth 2}
           (conform-xml [::f/Patient [::f/multipleBirthInteger {:value "2"}]]))))

  (testing "Observation with code"
    (is (= {:fhir/type :fhir/Observation
            :code
            #fhir/CodeableConcept
             {:coding
              [#fhir/Coding
                {:system #fhir/uri"http://loinc.org"
                 :code #fhir/code"39156-5"}]}}
           (conform-xml
            [::f/Observation
             [::f/code
              [::f/coding
               [::f/system {:value "http://loinc.org"}]
               [::f/code {:value "39156-5"}]]]]))))

  (testing "Patient with gender extension"
    (is (= {:fhir/type :fhir/Patient
            :gender
            #fhir/code
             {:extension
              [#fhir/Extension
                {:url "http://fhir.de/StructureDefinition/gender-amtlich-de"
                 :value
                 #fhir/Coding
                  {:system #fhir/uri"http://fhir.de/CodeSystem/gender-amtlich-de"
                   :code #fhir/code"D"
                   :display #fhir/string"divers"}}]
              :value "other"}}
           (conform-xml
            [:Patient
             [:gender
              {:value "other"}
              [:extension {:url "http://fhir.de/StructureDefinition/gender-amtlich-de"}
               [:valueCoding
                [:system {:value "http://fhir.de/CodeSystem/gender-amtlich-de"}]
                [:code {:value "D"}]
                [:display {:value "divers"}]]]]]))))

  (testing "patient resource with mixed character content"
    (is (= {:fhir/type :fhir/Patient :id "0"}
           (conform-xml [::f/Patient "" [::f/id {:value "0"}]]))))

  (testing "questionnaire resource with item groups"
    (is (= {:fhir/type :fhir/Questionnaire
            :item
            [{:fhir/type :fhir.Questionnaire/item
              :type #fhir/code"group"
              :item
              [{:fhir/type :fhir.Questionnaire/item
                :type #fhir/code"string"
                :text "foo"}]}]}
           (conform-xml
            [::f/Questionnaire
             [::f/item
              [::f/type {:value "group"}]
              [::f/item
               [::f/type {:value "string"}]
               [::f/text {:value "foo"}]]]])))))

(deftest unform-xml-test
  (testing "Patient with id"
    (let [xml (sexp [::f/Patient {:xmlns "http://hl7.org/fhir"} [::f/id {:value "0"}]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml))))))

  (testing "Patient with deceasedBoolean"
    (let [xml (sexp [::f/Patient {:xmlns "http://hl7.org/fhir"} [::f/deceasedBoolean {:value "true"}]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml))))))

  (testing "Patient with deceasedDateTime"
    (let [xml (sexp [::f/Patient {:xmlns "http://hl7.org/fhir"} [::f/deceasedDateTime {:value "2020"}]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml))))))

  (testing "Patient with multipleBirthBoolean"
    (let [xml (sexp [::f/Patient {:xmlns "http://hl7.org/fhir"} [::f/multipleBirthBoolean {:value "false"}]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml))))))

  (testing "Patient with multipleBirthInteger"
    (let [xml (sexp [::f/Patient {:xmlns "http://hl7.org/fhir"} [::f/multipleBirthInteger {:value "2"}]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml))))))

  (testing "Patient with one name"
    (let [xml (sexp [::f/Patient {:xmlns "http://hl7.org/fhir"} [::f/name [::f/family {:value "Doe"}]]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml))))))

  (testing "Patient with two names"
    (let [xml (sexp
               [::f/Patient {:xmlns "http://hl7.org/fhir"}
                [::f/name [::f/family {:value "One"}]]
                [::f/name [::f/family {:value "Two"}]]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml))))))

  (testing "Patient with gender extension"
    (is (= (sexp
            [::f/Patient {:xmlns "http://hl7.org/fhir"}
             [::f/gender
              {:value "other"}
              [::f/extension {:url "http://fhir.de/StructureDefinition/gender-amtlich-de"}
               [::f/valueCoding
                [::f/system {:value "http://fhir.de/CodeSystem/gender-amtlich-de"}]
                [::f/code {:value "D"}]
                [::f/display {:value "divers"}]]]]])
           (fhir-spec/unform-xml
            {:fhir/type :fhir/Patient
             :gender
             #fhir/code
              {:extension
               [#fhir/Extension
                 {:url "http://fhir.de/StructureDefinition/gender-amtlich-de"
                  :value
                  #fhir/Coding
                   {:system #fhir/uri"http://fhir.de/CodeSystem/gender-amtlich-de"
                    :code #fhir/code"D"
                    :display #fhir/string"divers"}}]
               :value "other"}}))))

  (testing "Patient with Narrative"
    (let [xml (sexp [::f/Patient {:xmlns "http://hl7.org/fhir"}
                     [::f/id {:value "0"}]
                     [::f/text
                      [::xhtml/div {:xmlns "http://www.w3.org/1999/xhtml"}
                       [::xhtml/p "FHIR is cool."]]]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml))))))

  (testing "Observation with valueQuantity"
    (let [xml (sexp
               [::f/Observation {:xmlns "http://hl7.org/fhir"}
                [::f/valueQuantity
                 [::f/value {:value "36.6"}]
                 [::f/unit {:value "kg/m2"}]
                 [::f/system {:value "http://unitsofmeasure.org"}]
                 [::f/code {:value "kg/m2"}]]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml))))))

  (testing "Bundle with one resource"
    (let [xml (sexp
               [::f/Bundle {:xmlns "http://hl7.org/fhir"}
                [::f/entry
                 [::f/resource
                  [::f/Patient {:xmlns "http://hl7.org/fhir"}
                   [::f/id {:value "0"}]]]]])]
      (is (= xml (fhir-spec/unform-xml (fhir-spec/conform-xml xml)))))))

(deftest explain-data-xml-test
  (testing "valid resources"
    (are [resource] (nil? (fhir-spec/explain-data-xml resource))
      (sexp [::f/Patient [::f/id {:value "."}]])
      (sexp [::f/Patient [::f/id {:value "0"}]])))

  (testing "missing resource type"
    (given (fhir-spec/explain-data-xml {})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "value"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Invalid resource element `{}`."))

  (testing "unknown resource type"
    (given (fhir-spec/explain-data-xml {:tag "<unknown>"})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "value"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Unknown resource type `<unknown>`."))

  (testing "invalid resource"
    (given (fhir-spec/explain-data-xml
            (sexp [::f/Patient [::f/name [::f/use {:value ""}]]]))
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `code`, regex `[\\u0021-\\uFFFF]+([ \\t\\n\\r][\\u0021-\\uFFFF]+)*`."
      ;; TODO: implement expression for XML
      (comment [:fhir/issues 0 :fhir.issues/expression] := "name[0].use")))

  (testing "Bundle with invalid Patient gender"
    (given (fhir-spec/explain-data-xml
            (sexp
             [::f/Bundle
              [::f/entry
               [::f/resource
                [::f/Patient [::f/gender {:value " "}]]]]]))
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ` `. Expected type is `code`, regex `[\\u0021-\\uFFFF]+([ \\t\\n\\r][\\u0021-\\uFFFF]+)*`."
      ;; TODO: implement expression for XML
      (comment
        [:fhir/issues 0 :fhir.issues/expression] :=
        "entry[0].resource.gender"))))

;; ---- Primitive Types -------------------------------------------------------

(defn- emit [element]
  (xml/emit-str (assoc element :tag :foo)))

(deftest fhir-boolean-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/boolean-value]
            (= (type/boolean value) (s2/conform :fhir.xml/boolean (sexp-value (str value))))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/boolean-value (gen/return nil)])
                           character-content gen/string-ascii]
              (= (type/boolean {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.xml/boolean
                             (sexp
                              [nil (cond-> {} id (assoc :id id) (some? value) (assoc :value (str value)))
                               character-content [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/boolean (sexp-value v)))
          "a"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/boolean-value]
            (= (sexp-value (str value)) (s2/unform :fhir.xml/boolean (type/boolean value)))))

        (testing "emit"
          (satisfies-prop 100
            (prop/for-all [value fg/boolean-value]
              (emit (s2/unform :fhir.xml/boolean (type/boolean value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/boolean-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) (some? value) (assoc :value (str value)))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/boolean
                          (type/boolean {:id id
                                         :extension
                                         [(type/extension {:url extension-url})]
                                         :value value})))))))))

(deftest fhir-integer-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/integer-value]
            (= (type/integer value) (s2/conform :fhir.xml/integer (sexp-value (str value))))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/integer-value (gen/return nil)])
                           character-content gen/string-ascii]
              (= (type/integer {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.xml/integer
                             (sexp
                              [nil (cond-> {} id (assoc :id id) (some? value) (assoc :value (str value)))
                               character-content [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/integer (sexp-value v)))
          "a"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/integer-value]
            (= (sexp-value (str value)) (s2/unform :fhir.xml/integer (type/integer value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/integer-value]
              (emit (s2/unform :fhir.xml/integer (type/integer value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/integer-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) (some? value) (assoc :value (str value)))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/integer
                          (type/integer {:id id
                                         :extension
                                         [(type/extension {:url extension-url})]
                                         :value value})))))))))

(deftest fhir-string-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/string-value]
            (= (type/string value) (s2/conform :fhir.xml/string (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/string-value (gen/return nil)])
                           character-content gen/string-ascii]
              (= (type/string {:id id
                               :extension
                               [(type/extension {:url extension-url})]
                               :value value})
                 (s2/conform :fhir.xml/string
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               character-content [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/string (sexp-value v)))
          ""
          "\u001e"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/string-value]
            (= (sexp-value value) (s2/unform :fhir.xml/string (type/string value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/string-value]
              (emit (s2/unform :fhir.xml/string (type/string value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/string-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/string
                          (type/string {:id id
                                        :extension
                                        [(type/extension {:url extension-url})]
                                        :value value})))))))))

(deftest fhir-decimal-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/decimal-value]
            (= (type/decimal value) (s2/conform :fhir.xml/decimal (sexp-value (str value))))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/decimal-value (gen/return nil)])
                           character-content gen/string-ascii]
              (= (type/decimal {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.xml/decimal
                             (sexp
                              [nil (cond-> {} id (assoc :id id) (some? value) (assoc :value (str value)))
                               character-content [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/decimal (sexp-value v)))
          "a"
          "\u001e"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/decimal-value]
            (= (sexp-value (str value)) (s2/unform :fhir.xml/decimal (type/decimal value))))))

      (testing "with extension"
        (satisfies-prop 1000
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/decimal-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) (some? value) (assoc :value (str value)))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/decimal
                          (type/decimal {:id id
                                         :extension
                                         [(type/extension {:url extension-url})]
                                         :value value})))))))))

(deftest fhir-uri-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/uri-value]
            (= (type/uri value) (s2/conform :fhir.xml/uri (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/uri-value (gen/return nil)])]
              (= (type/uri {:id id
                            :extension
                            [(type/extension {:url extension-url})]
                            :value value})
                 (s2/conform :fhir.xml/uri
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/uri (sexp-value v)))
          " "))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/uri-value]
            (= (sexp-value value) (s2/unform :fhir.xml/uri (type/uri value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/uri-value]
              (emit (s2/unform :fhir.xml/uri (type/uri value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/uri-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/uri
                          (type/uri {:id id
                                     :extension
                                     [(type/extension {:url extension-url})]
                                     :value value})))))))))

(deftest fhir-url-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/url-value]
            (= (type/url value) (s2/conform :fhir.xml/url (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/url-value (gen/return nil)])]
              (= (type/url {:id id
                            :extension
                            [(type/extension {:url extension-url})]
                            :value value})
                 (s2/conform :fhir.xml/url
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/url (sexp-value v)))
          " "
          "\u001e"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/url-value]
            (= (sexp-value value) (s2/unform :fhir.xml/url (type/url value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/url-value]
              (emit (s2/unform :fhir.xml/url (type/url value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/url-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/url
                          (type/url {:id id
                                     :extension
                                     [(type/extension {:url extension-url})]
                                     :value value})))))))))

(deftest fhir-canonical-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/canonical-value]
            (= (type/canonical value) (s2/conform :fhir.xml/canonical (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/canonical-value (gen/return nil)])]
              (= (type/canonical {:id id
                                  :extension
                                  [(type/extension {:url extension-url})]
                                  :value value})
                 (s2/conform :fhir.xml/canonical
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/canonical (sexp-value v)))
          " "
          "\u001e"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/canonical-value]
            (= (sexp-value value) (s2/unform :fhir.xml/canonical (type/canonical value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/canonical-value]
              (emit (s2/unform :fhir.xml/canonical (type/canonical value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/canonical-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/canonical
                          (type/canonical {:id id
                                           :extension
                                           [(type/extension {:url extension-url})]
                                           :value value})))))))))

(deftest fhir-base64Binary-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/base64Binary-value]
            (= (type/base64Binary value) (s2/conform :fhir.xml/base64Binary (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/base64Binary-value (gen/return nil)])]
              (= (type/base64Binary {:id id
                                     :extension
                                     [(type/extension {:url extension-url})]
                                     :value value})
                 (s2/conform :fhir.xml/base64Binary
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/base64Binary (sexp-value v)))
          ""))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/base64Binary-value]
            (= (sexp-value value) (s2/unform :fhir.xml/base64Binary (type/base64Binary value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/base64Binary-value]
              (emit (s2/unform :fhir.xml/base64Binary (type/base64Binary value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/base64Binary-value
                         value (gen/one-of [fg/base64Binary-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/base64Binary
                          (type/base64Binary {:id id
                                              :extension
                                              [(type/extension {:url extension-url})]
                                              :value value})))))))))

(deftest fhir-instant-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/instant-value]
            (= (type/instant value) (s2/conform :fhir.xml/instant (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/instant-value (gen/return nil)])]
              (= (type/instant {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.xml/instant
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]]))))))

        (testing "invalid"
          (are [v] (s2/invalid? (s2/conform :fhir.xml/instant (sexp-value v)))
            "2019-13"
            "2019-02-29")))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/instant-value]
            (= (sexp-value value) (s2/unform :fhir.xml/instant (type/instant value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/instant-value]
              (emit (s2/unform :fhir.xml/instant (type/instant value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/instant-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/instant
                          (type/instant {:id id
                                         :extension
                                         [(type/extension {:url extension-url})]
                                         :value value})))))))))

(deftest fhir-date-test
  (testing "valid"
    (are [x] (s2/valid? :fhir/date x)
      #fhir/date{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}))

  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/date-value]
            (= (type/date value) (s2/conform :fhir.xml/date (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/date-value (gen/return nil)])
                           character-content gen/string-ascii]
              (= (type/date {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.xml/date
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               character-content [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/date (sexp-value v)))
          "2019-13"
          "2019-02-29"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/date-value]
            (= (sexp-value value) (s2/unform :fhir.xml/date (type/date value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/date-value]
              (emit (s2/unform :fhir.xml/date (type/date value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/date-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/date
                          (type/date {:id id
                                      :extension
                                      [(type/extension {:url extension-url})]
                                      :value value})))))))))

(deftest fhir-dateTime-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value (fg/dateTime-value)]
            (= (type/dateTime value) (s2/conform :fhir.xml/dateTime (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [(fg/dateTime-value) (gen/return nil)])]
              (= (type/dateTime {:id id
                                 :extension
                                 [(type/extension {:url extension-url})]
                                 :value value})
                 (s2/conform :fhir.xml/dateTime
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]]))))))

        (testing "invalid"
          (are [v] (s2/invalid? (s2/conform :fhir.xml/dateTime (sexp-value v)))
            "2019-13"
            "2019-02-29")))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value (fg/dateTime-value)]
            (= (sexp-value value) (s2/unform :fhir.xml/dateTime (type/dateTime value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value (fg/dateTime-value)]
              (emit (s2/unform :fhir.xml/dateTime (type/dateTime value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [(fg/dateTime-value) (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/dateTime
                          (type/dateTime {:id id
                                          :extension
                                          [(type/extension {:url extension-url})]
                                          :value value})))))))))

(deftest fhir-time-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/time-value]
            (= (type/time value) (s2/conform :fhir.xml/time (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/time-value (gen/return nil)])]
              (= (type/time {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.xml/time
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]]))))))

        (testing "invalid"
          (are [v] (s2/invalid? (s2/conform :fhir.xml/time (sexp-value v)))
            "24:00"
            "24:00:00")))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/time-value]
            (= (sexp-value value) (s2/unform :fhir.xml/time (type/time value)))))

        (testing "emit"
          (satisfies-prop 100
            (prop/for-all [value fg/time-value]
              (emit (s2/unform :fhir.xml/time (type/time value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/time-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/time
                          (type/time {:id id
                                      :extension
                                      [(type/extension {:url extension-url})]
                                      :value value})))))))))

(deftest fhir-code-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/code-value]
            (= (type/code value) (s2/conform :fhir.xml/code (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/code-value (gen/return nil)])]
              (= (type/code {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.xml/code
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/code (sexp-value v)))
          ""
          "\u001e"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/code-value]
            (= (sexp-value value) (s2/unform :fhir.xml/code (type/code value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/code-value]
              (emit (s2/unform :fhir.xml/code (type/code value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/code-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/code
                          (type/code {:id id
                                      :extension
                                      [(type/extension {:url extension-url})]
                                      :value value})))))))))

(deftest fhir-oid-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/oid-value]
            (= (type/oid value) (s2/conform :fhir.xml/oid (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/oid-value (gen/return nil)])]
              (= (type/oid {:id id
                            :extension
                            [(type/extension {:url extension-url})]
                            :value value})
                 (s2/conform :fhir.xml/oid
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/oid (sexp-value v)))
          ""))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/oid-value]
            (= (sexp-value value) (s2/unform :fhir.xml/oid (type/oid value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/oid-value]
              (emit (s2/unform :fhir.xml/oid (type/oid value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/oid-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/oid
                          (type/oid {:id id
                                     :extension
                                     [(type/extension {:url extension-url})]
                                     :value value})))))))))

(deftest fhir-id-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/id-value]
            (= (type/id value) (s2/conform :fhir.xml/id (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/id-value (gen/return nil)])]
              (= (type/id {:id id
                           :extension
                           [(type/extension {:url extension-url})]
                           :value value})
                 (s2/conform :fhir.xml/id
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/id (sexp-value v)))
          ""))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/id-value]
            (= (sexp-value value) (s2/unform :fhir.xml/id (type/id value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/id-value]
              (emit (s2/unform :fhir.xml/id (type/id value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/id-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/id
                          (type/id {:id id
                                    :extension
                                    [(type/extension {:url extension-url})]
                                    :value value})))))))))

(deftest fhir-markdown-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/markdown-value]
            (= (type/markdown value) (s2/conform :fhir.xml/markdown (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/markdown-value (gen/return nil)])]
              (= (type/markdown {:id id
                                 :extension
                                 [(type/extension {:url extension-url})]
                                 :value value})
                 (s2/conform :fhir.xml/markdown
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/markdown (sexp-value v)))
          ""
          "\u001e"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/markdown-value]
            (= (sexp-value value) (s2/unform :fhir.xml/markdown (type/markdown value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/markdown-value]
              (emit (s2/unform :fhir.xml/markdown (type/markdown value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/markdown-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/markdown
                          (type/markdown {:id id
                                          :extension
                                          [(type/extension {:url extension-url})]
                                          :value value})))))))))

(deftest fhir-unsignedInt-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/unsignedInt-value]
            (= (type/unsignedInt value) (s2/conform :fhir.xml/unsignedInt (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/unsignedInt-value (gen/return nil)])]
              (= (type/unsignedInt {:id id
                                    :extension
                                    [(type/extension {:url extension-url})]
                                    :value value})
                 (s2/conform :fhir.xml/unsignedInt
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/unsignedInt (sexp-value v)))
          ""))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/unsignedInt-value]
            (= (sexp-value value) (s2/unform :fhir.xml/unsignedInt (type/unsignedInt value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/unsignedInt-value]
              (emit (s2/unform :fhir.xml/unsignedInt (type/unsignedInt value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/unsignedInt-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/unsignedInt
                          (type/unsignedInt {:id id
                                             :extension
                                             [(type/extension {:url extension-url})]
                                             :value value})))))))))

(deftest fhir-positiveInt-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/positiveInt-value]
            (= (type/positiveInt value) (s2/conform :fhir.xml/positiveInt (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/positiveInt-value (gen/return nil)])]
              (= (type/positiveInt {:id id
                                    :extension
                                    [(type/extension {:url extension-url})]
                                    :value value})
                 (s2/conform :fhir.xml/positiveInt
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/positiveInt (sexp-value v)))
          ""))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/positiveInt-value]
            (= (sexp-value value) (s2/unform :fhir.xml/positiveInt (type/positiveInt value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/positiveInt-value]
              (emit (s2/unform :fhir.xml/positiveInt (type/positiveInt value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/positiveInt-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/positiveInt
                          (type/positiveInt {:id id
                                             :extension
                                             [(type/extension {:url extension-url})]
                                             :value value})))))))))

(deftest fhir-uuid-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/uuid-value]
            (= (type/uuid value) (s2/conform :fhir.xml/uuid (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/uuid-value (gen/return nil)])]
              (= (type/uuid {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.xml/uuid
                             (sexp
                              [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                               [::f/extension {:url extension-url}]])))))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/uuid (sexp-value v)))
          ""))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 1000
          (prop/for-all [value fg/uuid-value]
            (= (sexp-value value) (s2/unform :fhir.xml/uuid (type/uuid value)))))

        (testing "emit"
          (satisfies-prop 1000
            (prop/for-all [value fg/uuid-value]
              (emit (s2/unform :fhir.xml/uuid (type/uuid value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/uuid-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/uuid
                          (type/uuid {:id id
                                      :extension
                                      [(type/extension {:url extension-url})]
                                      :value value})))))))))

(def xhtml-element
  (sexp
   [::xhtml/div {:xmlns "http://www.w3.org/1999/xhtml"}
    [::xhtml/p "FHIR is cool."]]))

(deftest fhir-xhtml-test
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/xhtml s)
      #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"></div>"))

  (testing "parsing"
    (testing "XML"
      (is (= #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"
             (s2/conform :fhir.xml/xhtml xhtml-element)))))

  (testing "writing"
    (testing "XML"
      (is (= xhtml-element
             (s2/unform :fhir.xml/xhtml #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"))))))

;; ---- Complex Types ---------------------------------------------------------

(deftest attachment-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/attachment)]
          (s2/valid? :fhir/Attachment x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Attachment x))
        #fhir/Attachment{:contentType "foo"})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/attachment)]
          (= (->> (write-json x)
                  (parse-json "Attachment"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/attachment)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Attachment))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/attachment)]
          (= (->> (write-cbor x)
                  (parse-cbor "Attachment"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Attachment" json))
        {}
        #fhir/Attachment{}

        {:contentType "code-115735"}
        #fhir/Attachment{:contentType #fhir/code"code-115735"}

        {:contentType "code-115735"
         :_contentType {:extension [{:url "url-101510"}]}}
        #fhir/Attachment
         {:contentType
          #fhir/code{:value "code-115735"
                     :extension [#fhir/Extension{:url "url-101510"}]}}

        {:_contentType {:id "id-205332"}}
        #fhir/Attachment{:contentType #fhir/code{:id "id-205332"}}

        {:_contentType {:extension [{:url "url-101510"}]}}
        #fhir/Attachment
         {:contentType
          #fhir/code{:extension [#fhir/Extension{:url "url-101510"}]}}

        {:data "MTA1NjE0Cg=="}
        #fhir/Attachment{:data #fhir/base64Binary"MTA1NjE0Cg=="}

        {:_data {:extension [{:url "url-115417"}]}}
        #fhir/Attachment{:data #fhir/base64Binary{:extension [#fhir/Extension{:url "url-115417"}]}}

        {:_url {:extension [{:url "url-130143"}]}}
        #fhir/Attachment{:url #fhir/url{:extension [#fhir/Extension{:url "url-130143"}]}}

        {:_size {:extension [{:url "url-130946"}]}}
        #fhir/Attachment{:size #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-130946"}]}}

        {:size 204737
         :_size {:extension [{:url "url-131115"}]}}
        #fhir/Attachment{:size #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-131115"}] :value 204737}}

        {:_creation {:extension [{:url "url-132312"}]}}
        #fhir/Attachment{:creation #fhir/dateTime{:extension [#fhir/Extension{:url "url-132312"}]}})

      (testing "unknown keys are ignored"
        (given (write-parse-json "Attachment" {::unknown "unknown"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Unknown property `blaze.fhir.spec-test/unknown`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Unknown property `blaze.fhir.spec-test/unknown`."
          [:fhir/issues 0 :fhir.issues/expression] := "Attachment"))

      (testing "invalid underscore properties are ignored"
        (given (write-parse-json "Attachment" {:_contentType "foo"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `primitive extension map`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `primitive extension map`."
          [:fhir/issues 0 :fhir.issues/expression] := "Attachment.contentType"))

      (testing "invalid"
        (given (write-parse-json "Attachment" {:contentType 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/expression] := "Attachment.contentType")))

    (testing "XML"
      (are [xml fhir] (= fhir (s2/conform :fhir.xml/Attachment xml))
        (sexp [::f/Attachment])
        #fhir/Attachment{}

        (sexp [::f/Attachment {} [::f/contentType {:value "code-115735"}]])
        #fhir/Attachment{:contentType #fhir/code"code-115735"}

        (sexp [::f/Attachment {} [::f/contentType {:value "code-115735"} [::f/extension {:url "url-101510"}]]])
        #fhir/Attachment{:contentType #fhir/code{:value "code-115735" :extension [#fhir/Extension{:url "url-101510"}]}}

        (sexp [::f/Attachment {} [::f/contentType {:id "id-205332"}]])
        #fhir/Attachment{:contentType #fhir/code{:id "id-205332"}}

        (sexp [::f/Attachment {} [::f/contentType {} [::f/extension {:url "url-101510"}]]])
        #fhir/Attachment{:contentType #fhir/code{:extension [#fhir/Extension{:url "url-101510"}]}}

        (sexp [::f/Attachment {} [::f/contentType {:value "x  x"}]])
        ::s2/invalid

        (sexp [::f/Attachment {} [::f/data {:value "MTA1NjE0Cg=="}]])
        #fhir/Attachment{:data #fhir/base64Binary"MTA1NjE0Cg=="}

        (sexp [::f/Attachment {} [::f/data {} [::f/extension {:url "url-115417"}]]])
        #fhir/Attachment{:data #fhir/base64Binary{:extension [#fhir/Extension{:url "url-115417"}]}}

        (sexp [::f/Attachment {} [::f/url {} [::f/extension {:url "url-130143"}]]])
        #fhir/Attachment{:url #fhir/url{:extension [#fhir/Extension{:url "url-130143"}]}}

        (sexp [::f/Attachment {} [::f/size {:value "204742"}]])
        #fhir/Attachment{:size #fhir/unsignedInt 204742}

        (sexp [::f/Attachment {} [::f/size {} [::f/extension {:url "url-130946"}]]])
        #fhir/Attachment{:size #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-130946"}]}})))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Attachment{}
        {}

        #fhir/Attachment{:id "id-155426"}
        {:id "id-155426"}

        #fhir/Attachment{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Attachment{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Attachment{:contentType #fhir/code"code-150209"}
        {:contentType "code-150209"}

        #fhir/Attachment{:contentType #fhir/code{:extension [#fhir/Extension{:url "url-101529"}]}}
        {:_contentType {:extension [{:url "url-101529"}]}}

        #fhir/Attachment{:contentType #fhir/code{:id "id-205332"}}
        {:_contentType {:id "id-205332"}}

        #fhir/Attachment{:contentType #fhir/code{:extension [#fhir/Extension{}] :value "code-150225"}}
        {:contentType "code-150225"
         :_contentType {:extension [{}]}}

        #fhir/Attachment{:contentType #fhir/code{:id "id-205544" :extension [#fhir/Extension{}] :value "code-150225"}}
        {:contentType "code-150225"
         :_contentType {:id "id-205544" :extension [{}]}}

        #fhir/Attachment{:language #fhir/code"de"}
        {:language "de"}

        #fhir/Attachment{:data #fhir/base64Binary"MTA1NjE0Cg=="}
        {:data "MTA1NjE0Cg=="}

        #fhir/Attachment{:data #fhir/base64Binary{:extension [#fhir/Extension{:url "url-115417"}]}}
        {:_data {:extension [{:url "url-115417"}]}}

        #fhir/Attachment{:data #fhir/base64Binary{:extension [#fhir/Extension{}] :value "MTA1NjE0Cg=="}}
        {:data "MTA1NjE0Cg=="
         :_data {:extension [{}]}}

        #fhir/Attachment{:url #fhir/url"url-210424"}
        {:url "url-210424"}

        #fhir/Attachment{:url #fhir/url{:extension [#fhir/Extension{:url "url-130143"}]}}
        {:_url {:extension [{:url "url-130143"}]}}

        #fhir/Attachment{:size #fhir/unsignedInt 204742}
        {:size 204742}

        #fhir/Attachment{:size #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-130946"}]}}
        {:_size {:extension [{:url "url-130946"}]}}

        #fhir/Attachment{:size #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-131115"}] :value 204737}}
        {:size 204737
         :_size {:extension [{:url "url-131115"}]}}

        #fhir/Attachment{:hash #fhir/base64Binary"MTA1NjE0Cg=="}
        {:hash "MTA1NjE0Cg=="}

        #fhir/Attachment{:title "title-210622"}
        {:title "title-210622"}

        #fhir/Attachment{:creation #fhir/dateTime{:extension [#fhir/Extension{:url "url-132312"}]}}
        {:_creation {:extension [{:url "url-132312"}]}}

        #fhir/Attachment{:creation #fhir/dateTime{:extension [#fhir/Extension{:url "url-132333"}] :value "2022"}}
        {:creation "2022"
         :_creation {:extension [{:url "url-132333"}]}}))

    (testing "XML"
      (are [fhir xml] (= xml (fhir-spec/unform-xml fhir))
        #fhir/Attachment{}
        (sexp [])

        #fhir/Attachment{:id "id-155426"}
        (sexp [nil {:id "id-155426"}])

        #fhir/Attachment{:extension [#fhir/Extension{}]}
        (sexp [nil {} [::f/extension]])

        #fhir/Attachment{:extension [#fhir/Extension{} #fhir/Extension{}]}
        (sexp [nil {} [::f/extension] [::f/extension]])

        #fhir/Attachment{:contentType #fhir/code"code-150209"}
        (sexp [nil {} [::f/contentType {:value "code-150209"}]])

        #fhir/Attachment{:contentType #fhir/code{:extension [#fhir/Extension{}]}}
        (sexp [nil {} [::f/contentType {} [::f/extension]]])

        #fhir/Attachment{:contentType #fhir/code{:id "id-205332"}}
        (sexp [nil {} [::f/contentType {:id "id-205332"}]])

        #fhir/Attachment{:contentType #fhir/code{:extension [#fhir/Extension{}] :value "code-150225"}}
        (sexp [nil {} [::f/contentType {:value "code-150225"} [::f/extension]]])

        #fhir/Attachment{:contentType #fhir/code{:id "id-205544" :extension [#fhir/Extension{}] :value "code-150225"}}
        (sexp [nil {} [::f/contentType {:id "id-205544" :value "code-150225"} [::f/extension]]])

        #fhir/Attachment{:language #fhir/code"de"}
        (sexp [nil {} [::f/language {:value "de"}]])

        #fhir/Attachment{:data #fhir/base64Binary"MTA1NjE0Cg=="}
        (sexp [nil {} [::f/data {:value "MTA1NjE0Cg=="}]])

        #fhir/Attachment{:data #fhir/base64Binary{:extension [#fhir/Extension{:url "url-115417"}]}}
        (sexp [nil {} [::f/data {} [::f/extension {:url "url-115417"}]]])

        #fhir/Attachment{:url #fhir/url"url-210424"}
        (sexp [nil {} [::f/url {:value "url-210424"}]])

        #fhir/Attachment{:url #fhir/url{:extension [#fhir/Extension{:url "url-130143"}]}}
        (sexp [nil {} [::f/url {} [::f/extension {:url "url-130143"}]]])

        #fhir/Attachment{:size #fhir/unsignedInt 204742}
        (sexp [nil {} [::f/size {:value "204742"}]])

        #fhir/Attachment{:size #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-130946"}]}}
        (sexp [nil {} [::f/size {} [::f/extension {:url "url-130946"}]]])

        #fhir/Attachment{:hash #fhir/base64Binary"MTA1NjE0Cg=="}
        (sexp [nil {} [::f/hash {:value "MTA1NjE0Cg=="}]])

        #fhir/Attachment{:title "title-210622"}
        (sexp [nil {} [::f/title {:value "title-210622"}]])

        #fhir/Attachment{:creation #fhir/dateTime"2021"}
        (sexp [nil {} [::f/creation {:value "2021"}]]))))

  (testing "summary parsing"
    (satisfies-prop 20
      (prop/for-all [attachment (fg/attachment)]
        (let [source (write-cbor attachment)
              attachment (parse-cbor "Attachment" source :summary)]
          (nil? (:data attachment)))))))

(deftest extension-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/extension :value (fg/codeable-concept))]
          (s2/valid? :fhir/Extension x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Extension x))
        #fhir/Extension{:url 1})))

  (testing "round-trip"
    (doseq [value-gen
            [(fg/base64Binary)
             (fg/boolean)
             (fg/canonical)
             (fg/code)
             (fg/date)
             (fg/dateTime)
             (fg/decimal)
             (fg/id)
             (fg/instant)
             (fg/integer)
             (fg/markdown)
             (fg/oid)
             (fg/positiveInt)
             (fg/string)
             (fg/time)
             (fg/unsignedInt)
             (fg/uri)
             (fg/url)
             (fg/uuid)
             (fg/address)
             (fg/attachment)
             (fg/codeable-concept)
             (fg/coding)
             (fg/human-name)
             (fg/identifier)
             (fg/period)
             (fg/quantity)
             (fg/ratio)
             (fg/meta)]]
      (testing "JSON"
        (satisfies-prop 20
          (prop/for-all [x (fg/extension :value value-gen)]
            (= (->> (write-json x)
                    (parse-json "Extension"))
               (->> (write-json x)
                    (parse-json "Extension")
                    (write-json)
                    (parse-json "Extension"))
               x))))

      (testing "XML"
        (satisfies-prop 20
          (prop/for-all [x (fg/extension :value value-gen)]
            (= (->> x
                    fhir-spec/unform-xml
                    (s2/conform :fhir.xml/Extension))
               x))))

      (testing "CBOR"
        (satisfies-prop 20
          (prop/for-all [x (fg/extension :value value-gen)]
            (= (->> (write-cbor x)
                    (parse-cbor "Extension"))
               (->> (write-cbor x)
                    (parse-cbor "Extension")
                    (write-cbor)
                    (parse-cbor "Extension"))
               x))))))

  (testing "parsing"
    (testing "JSON"
      (testing "urls are interned"
        (let [e1 (write-parse-json "Extension" {:url (String. "foo") :valueString "bar"})
              e2 (write-parse-json "Extension" {:url (String. "foo") :valueString "bar"})]
          (is (identical? (:url e1) (:url e2)))))

      (are [json fhir] (= fhir (write-parse-json "Extension" json))
        {:url "foo" :valueString "bar"}
        #fhir/Extension{:url "foo" :value #fhir/string"bar"}

        {:url "foo" :valueCode "bar"}
        #fhir/Extension{:url "foo" :value #fhir/code"bar"}

        {:url "foo" :valueReference {:reference "bar"}}
        #fhir/Extension{:url "foo" :value #fhir/Reference{:reference "bar"}}

        {:url "foo" :valueCodeableConcept {:text "bar"}}
        #fhir/Extension{:url "foo" :value #fhir/CodeableConcept{:text #fhir/string"bar"}}

        {:url "foo" :extension [{:url "bar" :_valueDateTime {:extension [{:url "baz" :valueCode "qux"}]}}]}
        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code"qux"}]}}]}))

    (testing "XML"
      (testing "urls are interned"
        (let [e1 (s2/conform :fhir.xml/Extension (sexp [nil {:url (String. "foo")} [::f/valueString {:value "bar"}]]))
              e2 (s2/conform :fhir.xml/Extension (sexp [nil {:url (String. "foo")} [::f/valueString {:value "bar"}]]))]
          (is (identical? (:url e1) (:url e2)))))

      (are [xml fhir] (= fhir (s2/conform :fhir.xml/Extension xml))
        (sexp [nil {:url "foo"} [::f/valueString {:value "bar"}]])
        #fhir/Extension{:url "foo" :value #fhir/string"bar"}

        (sexp [nil {:url "foo"} [::f/valueCode {:value "bar"}]])
        #fhir/Extension{:url "foo" :value #fhir/code"bar"}

        (sexp [nil {:url "foo"} [::f/valueReference {} [::f/reference {:value "bar"}]]])
        #fhir/Extension{:url "foo" :value #fhir/Reference{:reference "bar"}}

        (sexp [nil {:url "foo"} [::f/valueCodeableConcept {} [::f/text {:value "bar"}]]])
        #fhir/Extension{:url "foo" :value #fhir/CodeableConcept{:text #fhir/string"bar"}}

        (sexp [nil {:url "foo"} [::f/extension {:url "bar"} [::f/valueDateTime {} [::f/extension {:url "baz"} [::f/valueCode {:value "qux"}]]]]])
        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code"qux"}]}}]}))

    (testing "CBOR"
      (testing "urls are interned"
        (let [e1 (write-parse-cbor "Extension" {:url (String. "foo") :valueString "bar"})
              e2 (write-parse-cbor "Extension" {:url (String. "foo") :valueString "bar"})]
          (is (identical? (:url e1) (:url e2)))))

      (are [cbor fhir] (= fhir (write-parse-cbor "Extension" cbor))
        {:url "foo" :valueString "bar"}
        #fhir/Extension{:url "foo" :value #fhir/string"bar"}

        {:url "foo" :valueCode "bar"}
        #fhir/Extension{:url "foo" :value #fhir/code"bar"}

        {:url "foo" :valueReference {:reference "bar"}}
        #fhir/Extension{:url "foo" :value #fhir/Reference{:reference "bar"}}

        {:url "foo" :valueCodeableConcept {:text "bar"}}
        #fhir/Extension{:url "foo" :value #fhir/CodeableConcept{:text #fhir/string"bar"}}

        {:url "foo" :extension [{:url "bar" :_valueDateTime {:extension [{:url "baz" :valueCode "qux"}]}}]}
        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code"qux"}]}}]})))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Extension{}
        {}

        #fhir/Extension{:id "id-135149"}
        {:id "id-135149"}

        #fhir/Extension{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Extension{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Extension{:url "url-135208"}
        {:url "url-135208"}

        #fhir/Extension{:value #fhir/code"code-135234"}
        {:valueCode "code-135234"}

        #fhir/Extension{:value #fhir/CodeableConcept{}}
        {:valueCodeableConcept {}}

        #fhir/Extension{:value #uuid"935eb22d-cf35-4351-ae71-e517e49ebcbc"}
        {:valueUuid "urn:uuid:935eb22d-cf35-4351-ae71-e517e49ebcbc"}

        #fhir/Extension{:value #fhir/uuid{:id "id-172058" :value #uuid"935eb22d-cf35-4351-ae71-e517e49ebcbc"}}
        {:valueUuid "urn:uuid:935eb22d-cf35-4351-ae71-e517e49ebcbc"
         :_valueUuid {:id "id-172058"}}

        #fhir/Extension{:value #fhir/CodeableConcept{:text #fhir/string"text-104840"}}
        {:valueCodeableConcept {:text "text-104840"}}

        #fhir/Extension{:value #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri"system-105127"}]}}
        {:valueCodeableConcept {:coding [{:system "system-105127"}]}}

        #fhir/Extension{:value {:fhir/type :fhir/Annotation :text "text-105422"}}
        {:valueAnnotation {:text "text-105422"}}

        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code"qux"}]}}]}
        {:url "foo" :extension [{:url "bar" :_valueDateTime {:extension [{:url "baz" :valueCode "qux"}]}}]}))

    (testing "CBOR"
      (are [fhir cbor] (= cbor (read-cbor (write-cbor fhir)))
        #fhir/Extension{}
        {}

        #fhir/Extension{:id "id-135149"}
        {:id "id-135149"}

        #fhir/Extension{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Extension{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Extension{:url "url-135208"}
        {:url "url-135208"}

        #fhir/Extension{:value #fhir/code"code-135234"}
        {:valueCode "code-135234"}

        #fhir/Extension{:value #fhir/CodeableConcept{}}
        {:valueCodeableConcept {}}

        #fhir/Extension{:value #fhir/Address{}}
        {:valueAddress {}}

        #fhir/Extension{:value #fhir/Address{:city "foo"}}
        {:valueAddress {:city "foo"}}

        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code"qux"}]}}]}
        {:url "foo" :extension [{:url "bar" :_valueDateTime {:extension [{:url "baz" :valueCode "qux"}]}}]}))))

(deftest coding-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding)]
          (s2/valid? :fhir/Coding x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Coding x))
        #fhir/Coding{:system "foo"})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding)]
          (= (->> (write-json x)
                  (parse-json "Coding"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Coding))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding)]
          (= (->> (write-cbor x)
                  (parse-cbor "Coding"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Coding" json))
        {:system "foo" :code "bar"}
        #fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "Coding" json))
        {:system "foo" :code "bar"}
        #fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"})

      (testing "interning works"
        (is (identical?
             (write-parse-cbor "Coding" {:system "foo" :code "bar"})
             (write-parse-cbor "Coding" {:system "foo" :code "bar"}))))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Coding{}
        {}

        #fhir/Coding{:id "id-205424"}
        {:id "id-205424"}

        #fhir/Coding{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Coding{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Coding{:system #fhir/uri"system-185812"}
        {:system "system-185812"}

        #fhir/Coding{:version #fhir/string"version-185951"}
        {:version "version-185951"}

        #fhir/Coding{:code #fhir/code"code-190226"}
        {:code "code-190226"}

        #fhir/Coding{:display #fhir/string"display-190327"}
        {:display "display-190327"}))

    (testing "XML"
      (are [fhir xml] (= xml (fhir-spec/unform-xml fhir))
        #fhir/Coding{}
        (sexp [])

        #fhir/Coding{:id "id-101320"}
        (sexp [nil {:id "id-101320"}])

        #fhir/Coding{:extension [#fhir/Extension{}]}
        (sexp [nil {} [::f/extension]])

        #fhir/Coding{:extension [#fhir/Extension{} #fhir/Extension{}]}
        (sexp [nil {} [::f/extension] [::f/extension]])

        #fhir/Coding{:system #fhir/uri"system-185812"}
        (sexp [nil {} [::f/system {:value "system-185812"}]])

        #fhir/Coding{:version #fhir/uri"version-185951"}
        (sexp [nil {} [::f/version {:value "version-185951"}]])

        #fhir/Coding{:code #fhir/uri"code-190226"}
        (sexp [nil {} [::f/code {:value "code-190226"}]])

        #fhir/Coding{:display #fhir/uri"display-190327"}
        (sexp [nil {} [::f/display {:value "display-190327"}]])))))

(deftest codeable-concept-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept)]
          (s2/valid? :fhir/CodeableConcept x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/CodeableConcept x))
        #fhir/CodeableConcept{:text 1})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept)]
          (= (->> (write-json x)
                  (parse-json "CodeableConcept"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/CodeableConcept))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept)]
          (= (->> (write-cbor x)
                  (parse-cbor "CodeableConcept"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "CodeableConcept" json))
        {}
        #fhir/CodeableConcept{}
        {:coding [{}]}
        #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
        {:text "text-223528"}
        #fhir/CodeableConcept{:text #fhir/string"text-223528"}))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "CodeableConcept" json))
        {}
        #fhir/CodeableConcept{}
        {:coding [{}]}
        #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
        {:coding [{:system "foo" :code "bar"}]}
        #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}]}
        {:text "text-223528"}
        #fhir/CodeableConcept{:text #fhir/string"text-223528"})

      (testing "interning works"
        (is (identical?
             (write-parse-cbor "CodeableConcept" {:coding [{:system "foo" :code "bar"}]})
             (write-parse-cbor "CodeableConcept" {:coding [{:system "foo" :code "bar"}]}))))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/CodeableConcept{}
        {}

        #fhir/CodeableConcept{:id "id-134927"}
        {:id "id-134927"}

        #fhir/CodeableConcept{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/CodeableConcept{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
        {:coding [{}]}

        #fhir/CodeableConcept{:text #fhir/string"text-223528"}
        {:text "text-223528"}))))

(deftest quantity-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/quantity)]
          (s2/valid? :fhir/Quantity x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Quantity x))
        #fhir/Quantity{:value "1"})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/quantity)]
          (= (->> (write-json x)
                  (parse-json "Quantity"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/quantity)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Quantity))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/quantity)]
          (= (->> (write-cbor x)
                  (parse-cbor "Quantity"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Quantity" json))
        {}
        #fhir/Quantity{}

        {:value 1M}
        #fhir/Quantity{:value #fhir/decimal 1M})

      (testing "invalid"
        (given (write-parse-json "Quantity" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Quantity.value")))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "Quantity" json))
        {}
        #fhir/Quantity{}

        {:value 1M}
        #fhir/Quantity{:value #fhir/decimal 1M})

      (testing "invalid"
        (given (write-parse-cbor "Quantity" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Quantity.value"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Quantity{}
        {}

        #fhir/Quantity{:id "id-134908"}
        {:id "id-134908"}

        #fhir/Quantity{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Quantity{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Quantity{:value #fhir/decimal 1M}
        {:value 1}

        #fhir/Quantity{:comparator #fhir/code"code-153342"}
        {:comparator "code-153342"}

        #fhir/Quantity{:unit #fhir/string"string-153351"}
        {:unit "string-153351"}

        #fhir/Quantity{:system #fhir/uri"system-153337"}
        {:system "system-153337"}

        #fhir/Quantity{:code #fhir/code"code-153427"}
        {:code "code-153427"}))))

(deftest ratio-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/ratio)]
          (s2/valid? :fhir/Ratio x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Ratio x))
        #fhir/Ratio{:numerator "1"})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/ratio)]
          (= (->> (write-json x)
                  (parse-json "Ratio"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/ratio)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Ratio))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/ratio)]
          (= (->> (write-cbor x)
                  (parse-cbor "Ratio"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Ratio" json))
        {}
        #fhir/Ratio{}

        {:id "id-151304"}
        #fhir/Ratio{:id "id-151304"}

        {:extension [{}]}
        #fhir/Ratio{:extension [#fhir/Extension{}]}

        {:numerator {:value 1M}}
        #fhir/Ratio{:numerator #fhir/Quantity{:value #fhir/decimal 1M}})

      (testing "invalid"
        (given (write-parse-json "Ratio" {:numerator "foo"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `Quantity`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `Quantity`."
          [:fhir/issues 0 :fhir.issues/expression] := "Ratio.numerator"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Ratio{}
        {}

        #fhir/Ratio{:id "id-134428"}
        {:id "id-134428"}

        #fhir/Ratio{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Ratio{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Ratio{:numerator #fhir/Quantity{:value #fhir/decimal 1M}}
        {:numerator {:value 1}}

        #fhir/Ratio{:denominator #fhir/Quantity{:value #fhir/decimal 1M}}
        {:denominator {:value 1}}))))

(deftest period-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/period)]
          (s2/valid? :fhir/Period x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Period x))
        #fhir/Period{:start "2020"})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/period)]
          (= (->> (write-json x)
                  (parse-json "Period"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/period)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Period))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/period)]
          (= (->> (write-cbor x)
                  (parse-cbor "Period"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Period" json))
        {}
        #fhir/Period{}

        {:id "id-151304"}
        #fhir/Period{:id "id-151304"}

        {:extension [{}]}
        #fhir/Period{:extension [#fhir/Extension{}]}

        {:start "2020"}
        #fhir/Period{:start #fhir/dateTime"2020"})

      (testing "invalid"
        (given (write-parse-json "Period" {:start "foo"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `date-time`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `date-time`."
          [:fhir/issues 0 :fhir.issues/expression] := "Period.start"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Period{}
        {}

        #fhir/Period{:id "id-134428"}
        {:id "id-134428"}

        #fhir/Period{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Period{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Period{:start #fhir/dateTime"2020"}
        {:start "2020"}

        #fhir/Period{:end #fhir/dateTime"2020"}
        {:end "2020"}))))

(deftest identifier-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (s2/valid? :fhir/Identifier x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Identifier x))
        #fhir/Identifier{:use "usual"})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (= (->> (write-json x)
                  (parse-json "Identifier"))
             x))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Identifier))
             x))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (= (->> (write-cbor x)
                  (parse-cbor "Identifier"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Identifier" json))
        {}
        #fhir/Identifier{}

        {:use "usual"}
        #fhir/Identifier{:use #fhir/code"usual"}

        {:value "value-151311"}
        #fhir/Identifier{:value #fhir/string"value-151311"})

      (testing "invalid"
        (given (write-parse-json "Identifier" {:use 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/expression] := "Identifier.use"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Identifier{}
        {}

        #fhir/Identifier{:id "id-155426"}
        {:id "id-155426"}

        #fhir/Identifier{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Identifier{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Identifier{:use #fhir/code"use-155449"}
        {:use "use-155449"}

        #fhir/Identifier{:type #fhir/CodeableConcept{}}
        {:type {}}

        #fhir/Identifier{:system #fhir/uri"system-160011"}
        {:system "system-160011"}

        #fhir/Identifier{:value "value-160034"}
        {:value "value-160034"}

        #fhir/Identifier{:period #fhir/Period{}}
        {:period {}}

        #fhir/Identifier{:assigner #fhir/Reference{}}
        {:assigner {}}))

    (testing "XML"
      (are [fhir xml] (= xml (fhir-spec/unform-xml fhir))
        #fhir/Identifier{}
        (sexp [])

        #fhir/Identifier{:id "id-155426"}
        (sexp [nil {:id "id-155426"}])

        #fhir/Identifier{:extension [#fhir/Extension{}]}
        (sexp [nil {} [::f/extension]])

        #fhir/Identifier{:extension [#fhir/Extension{} #fhir/Extension{}]}
        (sexp [nil {} [::f/extension] [::f/extension]])

        #fhir/Identifier{:use #fhir/code"use-155449"}
        (sexp [nil {} [::f/use {:value "use-155449"}]])

        #fhir/Identifier{:type #fhir/CodeableConcept{}}
        (sexp [nil {} [::f/type]])

        #fhir/Identifier{:system #fhir/uri"system-160011"}
        (sexp [nil {} [::f/system {:value "system-160011"}]])

        #fhir/Identifier{:value "value-160034"}
        (sexp [nil {} [::f/value {:value "value-160034"}]])

        #fhir/Identifier{:period #fhir/Period{}}
        (sexp [nil {} [::f/period]])

        #fhir/Identifier{:assigner #fhir/Reference{}}
        (sexp [nil {} [::f/assigner]])))))

(deftest human-name-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/human-name)]
          (s2/valid? :fhir/HumanName x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/HumanName x))
        #fhir/HumanName{:use "usual"})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/human-name)]
          (= (->> (write-json x)
                  (parse-json "HumanName"))
             (->> (write-json x)
                  (parse-json "HumanName")
                  (write-json)
                  (parse-json "HumanName"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/human-name)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/HumanName))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/human-name)]
          (= (->> (write-cbor x)
                  (parse-cbor "HumanName"))
             (->> (write-cbor x)
                  (parse-cbor "HumanName")
                  (write-cbor)
                  (parse-cbor "HumanName"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "HumanName" json))
        {}
        #fhir/HumanName{}

        {:use "usual"}
        #fhir/HumanName{:use #fhir/code"usual"}

        {:given ["given-212441"]}
        #fhir/HumanName{:given ["given-212441"]}

        {:_given [{:extension [{:url "url-143610"}]}]}
        #fhir/HumanName{:given [#fhir/string{:extension [#fhir/Extension{:url "url-143610"}]}]}

        {:given ["given-143625"]
         :_given [{:extension [{:url "url-143619"}]}]}
        #fhir/HumanName{:given [#fhir/string{:extension [#fhir/Extension{:url "url-143619"}] :value "given-143625"}]}

        {:given ["given-212448" "given-212454"]}
        #fhir/HumanName{:given ["given-212448" "given-212454"]}

        {:given ["given-143759" "given-143809"]
         :_given [{:extension [{:url "url-143750"}]} {:extension [{:url "url-143806"}]}]}
        #fhir/HumanName
         {:given
          [#fhir/string{:extension [#fhir/Extension{:url "url-143750"}]
                        :value "given-143759"}
           #fhir/string{:extension [#fhir/Extension{:url "url-143806"}]
                        :value "given-143809"}]}

        {:given ["given-143759" nil]
         :_given [nil {:extension [{:url "url-143806"}]}]}
        #fhir/HumanName
         {:given
          [#fhir/string"given-143759"
           #fhir/string{:extension [#fhir/Extension{:url "url-143806"}]}]})

      (testing "invalid"
        (given (write-parse-json "HumanName" {:use 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/expression] := "HumanName.use")))

    (testing "CBOR"
      (are [cbor fhir] (= fhir (write-parse-cbor "HumanName" cbor))
        {}
        #fhir/HumanName{}

        {:use "usual"}
        #fhir/HumanName{:use #fhir/code"usual"}

        {:given ["given-212441"]}
        #fhir/HumanName{:given ["given-212441"]})))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/HumanName{}
        {}

        #fhir/HumanName{:id "id-155426"}
        {:id "id-155426"}

        #fhir/HumanName{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/HumanName{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/HumanName{:use #fhir/code"use-155449"}
        {:use "use-155449"}

        #fhir/HumanName{:text "text-141140"}
        {:text "text-141140"}

        #fhir/HumanName{:family "family-141158"}
        {:family "family-141158"}

        #fhir/HumanName{:given ["given-212441"]}
        {:given ["given-212441"]}

        #fhir/HumanName{:given [#fhir/string{:extension [#fhir/Extension{:url "url-143610"}]}]}
        {:_given [{:extension [{:url "url-143610"}]}]}

        #fhir/HumanName{:given [#fhir/string{:extension [#fhir/Extension{:url "url-143619"}] :value "given-143625"}]}
        {:given ["given-143625"]
         :_given [{:extension [{:url "url-143619"}]}]}

        #fhir/HumanName{:given ["given-212448" "given-212454"]}
        {:given ["given-212448" "given-212454"]}

        #fhir/HumanName
         {:given
          [#fhir/string{:extension [#fhir/Extension{:url "url-143750"}]
                        :value "given-143759"}
           #fhir/string{:extension [#fhir/Extension{:url "url-143806"}]
                        :value "given-143809"}]}
        {:given ["given-143759" "given-143809"]
         :_given [{:extension [{:url "url-143750"}]} {:extension [{:url "url-143806"}]}]}

        #fhir/HumanName
         {:given
          [#fhir/string"given-143759"
           #fhir/string{:extension [#fhir/Extension{:url "url-143806"}]}]}
        {:given ["given-143759" nil]
         :_given [nil {:extension [{:url "url-143806"}]}]}

        #fhir/HumanName{:period #fhir/Period{}}
        {:period {}}))

    (testing "XML"
      (are [fhir xml] (= xml (fhir-spec/unform-xml fhir))
        #fhir/HumanName{}
        (sexp [])

        #fhir/HumanName{:id "id-155426"}
        (sexp [nil {:id "id-155426"}])

        #fhir/HumanName{:extension [#fhir/Extension{}]}
        (sexp [nil {} [::f/extension]])

        #fhir/HumanName{:extension [#fhir/Extension{} #fhir/Extension{}]}
        (sexp [nil {} [::f/extension] [::f/extension]])

        #fhir/HumanName{:use #fhir/code"use-155449"}
        (sexp [nil {} [::f/use {:value "use-155449"}]])

        #fhir/HumanName{:text "text-141140"}
        (sexp [nil {} [::f/text {:value "text-141140"}]])

        #fhir/HumanName{:family "family-141158"}
        (sexp [nil {} [::f/family {:value "family-141158"}]])

        #fhir/HumanName{:given ["given-212441"]}
        (sexp [nil {} [::f/given {:value "given-212441"}]])

        #fhir/HumanName{:given ["given-212441" "given-170006"]}
        (sexp [nil {}
               [::f/given {:value "given-212441"}]
               [::f/given {:value "given-170006"}]])

        #fhir/HumanName{:period #fhir/Period{}}
        (sexp [nil {} [::f/period]])))))

(deftest address-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/address)]
          (s2/valid? :fhir/Address x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Address x))
        #fhir/Address{:use "usual"})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [x (fg/address)]
          (= (->> (write-json x)
                  (parse-json "Address"))
             (->> (write-json x)
                  (parse-json "Address")
                  (write-json)
                  (parse-json "Address"))
             x))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [x (fg/address)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Address))
             x))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [x (fg/address)]
          (= (->> (write-cbor x)
                  (parse-cbor "Address"))
             (->> (write-cbor x)
                  (parse-cbor "Address")
                  (write-cbor)
                  (parse-cbor "Address"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Address" json))
        {}
        #fhir/Address{}

        {:use "usual"}
        #fhir/Address{:use #fhir/code"usual"})

      (testing "invalid"
        (given (write-parse-json "Address" {:use 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/expression] := "Address.use"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Address{}
        {}

        #fhir/Address{:id "id-155426"}
        {:id "id-155426"}

        #fhir/Address{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Address{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Address{:use #fhir/code"use-155449"}
        {:use "use-155449"}

        #fhir/Address{:text "text-170345"}
        {:text "text-170345"}

        #fhir/Address{:line ["line-171433"]}
        {:line ["line-171433"]}

        #fhir/Address{:line ["line-171433" "line-171857"]}
        {:line ["line-171433" "line-171857"]}

        #fhir/Address{:city "city-171937"}
        {:city "city-171937"}

        #fhir/Address{:district "district-171937"}
        {:district "district-171937"}

        #fhir/Address{:state "state-171937"}
        {:state "state-171937"}

        #fhir/Address{:postalCode "postalCode-171937"}
        {:postalCode "postalCode-171937"}

        #fhir/Address{:country "country-171937"}
        {:country "country-171937"}

        #fhir/Address{:period #fhir/Period{}}
        {:period {}}))

    (testing "XML"
      (are [fhir xml] (= xml (fhir-spec/unform-xml fhir))
        #fhir/Address{}
        (sexp [])

        #fhir/Address{:id "id-155426"}
        (sexp [nil {:id "id-155426"}])

        #fhir/Address{:extension [#fhir/Extension{}]}
        (sexp [nil {} [::f/extension]])

        #fhir/Address{:extension [#fhir/Extension{} #fhir/Extension{}]}
        (sexp [nil {} [::f/extension] [::f/extension]])

        #fhir/Address{:use #fhir/code"use-155449"}
        (sexp [nil {} [::f/use {:value "use-155449"}]])

        #fhir/Address{:text "text-170345"}
        (sexp [nil {} [::f/text {:value "text-170345"}]])

        #fhir/Address{:line ["line-171433"]}
        (sexp [nil {} [::f/line {:value "line-171433"}]])

        #fhir/Address{:line ["line-171433" "line-171857"]}
        (sexp [nil {}
               [::f/line {:value "line-171433"}]
               [::f/line {:value "line-171857"}]])

        #fhir/Address{:city "city-171937"}
        (sexp [nil {} [::f/city {:value "city-171937"}]])

        #fhir/Address{:district "district-171937"}
        (sexp [nil {} [::f/district {:value "district-171937"}]])

        #fhir/Address{:state "state-171937"}
        (sexp [nil {} [::f/state {:value "state-171937"}]])

        #fhir/Address{:postalCode "postalCode-171937"}
        (sexp [nil {} [::f/postalCode {:value "postalCode-171937"}]])

        #fhir/Address{:country "country-171937"}
        (sexp [nil {} [::f/country {:value "country-171937"}]])

        #fhir/Address{:period #fhir/Period{}}
        (sexp [nil {} [::f/period]])))))

(deftest reference-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/reference)]
          (s2/valid? :fhir/Reference x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Reference x))
        #fhir/Reference{:reference 1})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/reference)]
          (= (->> (write-json x)
                  (parse-json "Reference"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/reference)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Reference))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/reference)]
          (= (->> (write-cbor x)
                  (parse-cbor "Reference"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Reference" json))
        {}
        #fhir/Reference{}

        {:reference "Patient/1"}
        #fhir/Reference{:reference "Patient/1"})

      (testing "invalid"
        (given (write-parse-json "Reference" {:reference 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `string`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `string`."
          [:fhir/issues 0 :fhir.issues/expression] := "Reference.reference"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Reference{}
        {}

        #fhir/Reference{:id "id-155426"}
        {:id "id-155426"}

        #fhir/Reference{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Reference{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Reference{:reference "Patient/1"}
        {:reference "Patient/1"}

        #fhir/Reference{:type #fhir/uri"type-161222"}
        {:type "type-161222"}

        #fhir/Reference{:identifier #fhir/Identifier{}}
        {:identifier {}}

        #fhir/Reference{:display "display-161314"}
        {:display "display-161314"}))))

(deftest meta-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/meta)]
          (s2/valid? :fhir/Meta x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Meta x))
        #fhir/Identifier{:versionId "1"})))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [x (fg/meta)]
          (= (->> (write-json x)
                  (parse-json "Meta"))
             x))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [x (fg/meta)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Meta))
             x))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [x (fg/meta)]
          (= (->> (write-cbor x)
                  (parse-cbor "Meta"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Meta" json))
        {}
        #fhir/Meta{}

        {:versionId "1"}
        #fhir/Meta{:versionId #fhir/id"1"}

        {:lastUpdated "1970-01-01T00:00:00Z"}
        (type/meta {:lastUpdated Instant/EPOCH}))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Meta{}
        {}

        #fhir/Meta{:id "id-155426"}
        {:id "id-155426"}

        #fhir/Meta{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Meta{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Meta{:versionId #fhir/id"versionId-161812"}
        {:versionId "versionId-161812"}

        (type/meta {:lastUpdated Instant/EPOCH})
        {:lastUpdated "1970-01-01T00:00:00Z"}

        #fhir/Meta{:source #fhir/uri"source-162704"}
        {:source "source-162704"}

        #fhir/Meta{:profile [#fhir/canonical"profile-uri-145024"]}
        {:profile ["profile-uri-145024"]}

        #fhir/Meta{:security [#fhir/Coding{}]}
        {:security [{}]}

        #fhir/Meta{:security [#fhir/Coding{} #fhir/Coding{}]}
        {:security [{} {}]}

        #fhir/Meta{:tag [#fhir/Coding{}]}
        {:tag [{}]}

        #fhir/Meta{:tag [#fhir/Coding{} #fhir/Coding{}]}
        {:tag [{} {}]}))))

(deftest bundle-entry-search-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry-search)]
          (= (->> (write-json x)
                  (parse-json "Bundle.entry.search"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry-search)]
          (= (->> x
                  (s2/unform :fhir.xml.Bundle.entry/search)
                  (s2/conform :fhir.xml.Bundle.entry/search))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry-search)]
          (= (->> (write-cbor x)
                  (parse-cbor "Bundle.entry.search"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Bundle.entry.search" json))
        {}
        #fhir/BundleEntrySearch{}

        {:id "id-134805"}
        #fhir/BundleEntrySearch{:id "id-134805"}

        {:mode "match"}
        #fhir/BundleEntrySearch{:mode #fhir/code"match"})

      (testing "invalid"
        (given (write-parse-json "Bundle.entry.search" {:id 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `string`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `string`."
          [:fhir/issues 0 :fhir.issues/expression] := "Bundle.entry.search.id")))

    (testing "CBOR"
      (are [cbor fhir] (= fhir (write-parse-cbor "Bundle.entry.search" cbor))
        {}
        #fhir/BundleEntrySearch{}

        {:id "id-134805"}
        #fhir/BundleEntrySearch{:id "id-134805"}

        {:mode "match"}
        #fhir/BundleEntrySearch{:mode #fhir/code"match"})

      (testing "invalid"
        (given (write-parse-cbor "Bundle.entry.search" {:id 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `string`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `string`."
          [:fhir/issues 0 :fhir.issues/expression] := "Bundle.entry.search.id"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/BundleEntrySearch{}
        {}

        #fhir/BundleEntrySearch{:id "id-115229"}
        {:id "id-115229"}

        #fhir/BundleEntrySearch{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/BundleEntrySearch{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/BundleEntrySearch{:mode #fhir/code"match"}
        {:mode "match"}

        #fhir/BundleEntrySearch{:score 1.1M}
        {:score 1.1}))

    (testing "CBOR"
      (are [fhir cbor] (= cbor (read-cbor (write-cbor fhir)))
        #fhir/BundleEntrySearch{}
        {}

        #fhir/BundleEntrySearch{:id "id-115229"}
        {:id "id-115229"}

        #fhir/BundleEntrySearch{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/BundleEntrySearch{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/BundleEntrySearch{:mode #fhir/code"match"}
        {:mode "match"}

        #fhir/BundleEntrySearch{:score 1.1M}
        {:score 1.1M}))))

(deftest bundle-entry-reference-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry :resource (fg/patient))]
          (= (->> (write-json x)
                  (parse-json "Bundle.entry"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry :resource (fg/patient))]
          (= (->> x
                  (s2/unform :fhir.xml.Bundle/entry)
                  (s2/conform :fhir.xml.Bundle/entry))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry :resource (fg/patient))]
          (= (->> (write-cbor x)
                  (parse-cbor "Bundle.entry"))
             x)))))

  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json {:fhir/type :fhir.Bundle/entry})
             {}))
      (is (= (write-read-json
              {:fhir/type :fhir.Bundle/entry
               :fullUrl #fhir/uri"uri-155734"})
             {:fullUrl "uri-155734"}))))

  (testing "references"
    (satisfies-prop 10
      (prop/for-all [x (fg/bundle-entry
                        :resource
                        (fg/observation
                         :subject
                         (fg/reference
                          :reference (gen/return "Patient/0"))))]
        (empty? (type/references x))))))

;; ---- Resources -------------------------------------------------------------

(deftest capability-statement-test
  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json
              {:fhir/type :fhir/CapabilityStatement
               :rest
               [{:searchParam
                 [{:name #fhir/string"name-151346"}]}]})
             {:resourceType "CapabilityStatement"
              :rest
              [{:searchParam
                [{:name "name-151346"}]}]})))))

(deftest bundle-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [bundle (fg/bundle)]
          (= (->> (write-json bundle)
                  (parse-json "Bundle"))
             (->> (write-json bundle)
                  (parse-json "Bundle")
                  (write-json)
                  (parse-json "Bundle"))
             bundle))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [bundle (fg/bundle)]
          (= (-> bundle
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             bundle))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [bundle (fg/bundle)]
          (= (->> (write-cbor bundle)
                  (parse-cbor "Bundle"))
             (->> (write-cbor bundle)
                  (parse-cbor "Bundle")
                  (write-cbor)
                  (parse-cbor "Bundle"))
             bundle)))))

  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json {:fhir/type :fhir/Bundle})
             {:resourceType "Bundle"}))

      (is (= (write-read-json
              {:fhir/type :fhir/Bundle
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :fullUrl #fhir/uri"url-104116"}]})
             {:resourceType "Bundle" :entry [{:fullUrl "url-104116"}]}))

      (is (= (write-read-json
              {:fhir/type :fhir/Bundle
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :resource {:fhir/type :fhir/Patient}}]})
             {:resourceType "Bundle" :entry [{:resource {:resourceType "Patient"}}]})))))

(deftest patient-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [patient (fg/patient)]
          (= (->> (write-json patient)
                  (parse-json "Patient"))
             (->> (write-json patient)
                  (parse-json "Patient")
                  (write-json)
                  (parse-json "Patient"))
             patient))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [patient (fg/patient)]
          (= (-> patient
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             patient))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [patient (fg/patient)]
          (= (->> (write-cbor patient)
                  (parse-cbor "Patient"))
             (->> (write-cbor patient)
                  (parse-cbor "Patient")
                  (write-cbor)
                  (parse-cbor "Patient"))
             patient)))))

  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json {:fhir/type :fhir/Patient})
             {:resourceType "Patient"}))
      (is (= (write-read-json
              {:fhir/type :fhir/Patient
               :gender #fhir/code"female"
               :active #fhir/boolean true})
             {:resourceType "Patient" :active true :gender "female"})))))

(deftest list-test
  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/List
       :entry
       [{:fhir/type :fhir.List/entry
         :item #fhir/Reference{:reference "Patient/0"}}
        {:fhir/type :fhir.List/entry
         :item #fhir/Reference{:reference "Patient/1"}}]}
      [["Patient" "0"]
       ["Patient" "1"]])))

(def ^:private observation-non-summary-properties
  #{:category :dataAbsentReason :interpretation :note :bodySite :method
    :specimen :device :referenceRange})

(deftest observation-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [observation (fg/observation)]
          (= (->> (write-json observation)
                  (parse-json "Observation"))
             observation))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [observation (fg/observation)]
          (= (-> observation
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             observation))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [observation (fg/observation)]
          (= (->> (write-cbor observation)
                  (parse-cbor "Observation"))
             observation)))))

  (testing "summary parsing"
    (satisfies-prop 20
      (prop/for-all [observation (fg/observation)]
        (let [source (write-cbor observation)
              observation (parse-cbor "Observation" source :summary)]
          (and
           (->> observation :meta :tag (some fu/subsetted?))
           (not-any? observation-non-summary-properties (keys observation)))))))

  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json
              {:fhir/type :fhir/Observation
               :value #fhir/string{:id "id-201526" :value "value-201533"}})
             {:resourceType "Observation"
              :valueString "value-201533"
              :_valueString {:id "id-201526"}}))))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/Observation
       :subject #fhir/Reference{:reference "Patient/0"}}
      [["Patient" "0"]])))

(deftest procedure-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [procedure (fg/procedure)]
          (= (->> (write-json procedure)
                  (parse-json "Procedure"))
             procedure))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [procedure (fg/procedure)]
          (= (-> procedure
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             procedure))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [procedure (fg/procedure)]
          (= (->> (write-cbor procedure)
                  (parse-cbor "Procedure"))
             procedure)))))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/Procedure
       :instantiatesCanonical
       [#fhir/uri
         {:extension
          [#fhir/Extension
            {:value #fhir/Reference{:reference "Procedure/153904"}}]}
        #fhir/uri
         {:extension
          [#fhir/Extension
            {:value #fhir/Reference{:reference "Condition/153931"}}]}]
       :instantiatesUri
       [#fhir/uri
         {:extension
          [#fhir/Extension
            {:value #fhir/Reference{:reference "Patient/153540"}}]}
        #fhir/uri
         {:extension
          [#fhir/Extension
            {:value #fhir/Reference{:reference "Observation/153628"}}]}]}
      [["Procedure" "153904"]
       ["Condition" "153931"]
       ["Patient" "153540"]
       ["Observation" "153628"]])))

(deftest allergy-intolerance-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [allergy-intolerance (fg/allergy-intolerance)]
          (= (->> (write-json allergy-intolerance)
                  (parse-json "AllergyIntolerance"))
             allergy-intolerance))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [allergy-intolerance (fg/allergy-intolerance)]
          (= (-> allergy-intolerance
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             allergy-intolerance))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [allergy-intolerance (fg/allergy-intolerance)]
          (= (->> (write-cbor allergy-intolerance)
                  (parse-cbor "AllergyIntolerance"))
             allergy-intolerance))))))

(def ^:private code-system-non-summary-properties
  #{:description :purpose :copyright :concept})

(deftest code-system-test
  (testing "summary parsing"
    (satisfies-prop 20
      (prop/for-all [code-system (fg/code-system)]
        (let [source (write-cbor code-system)
              code-system (parse-cbor "CodeSystem" source :summary)]
          (and
           (->> code-system :meta :tag (some fu/subsetted?))
           (not-any? code-system-non-summary-properties (keys code-system))))))))

(def ^:private value-set-non-summary-properties
  #{:description :purpose :copyright :compose :expansion})

(deftest value-set-test
  (testing "summary parsing"
    (satisfies-prop 20
      (prop/for-all [value-set (fg/value-set)]
        (let [source (write-cbor value-set)
              value-set (parse-cbor "ValueSet" source :summary)]
          (and
           (->> value-set :meta :tag (some fu/subsetted?))
           (not-any? value-set-non-summary-properties (keys value-set))))))))

(deftest provenance-test
  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/Provenance
       :target
       [#fhir/Reference{:reference "Patient/204750"}
        #fhir/Reference{:reference "Observation/204754"}]}
      [["Patient" "204750"]
       ["Observation" "204754"]])))

(deftest task-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [task (fg/task)]
          (= (->> (write-json task)
                  (parse-json "Task"))
             task))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [task (fg/task)]
          (= (-> task
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             task))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [task (fg/task)]
          (= (->> (write-cbor task)
                  (parse-cbor "Task"))
             task)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Task" json))
        {:resourceType "Task"
         :output
         [{:valueReference {:reference "bar"}}]}
        {:fhir/type :fhir/Task
         :output
         [{:fhir/type :fhir.Task/output
           :value #fhir/Reference{:reference "bar"}}]})

      (testing "bare value properties are result in an error"
        (given (write-parse-json "Task" {:resourceType "Task"
                                         :output
                                         [{:value {:reference "bar"}}]})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Unknown property `value`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Unknown property `value`."
          [:fhir/issues 0 :fhir.issues/expression] := "Task.output[0]"))))

  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json
              {:fhir/type :fhir/Task
               :input
               [{:fhir/type :fhir.Task/input
                 :value #fhir/code"code-173329"}]})
             {:resourceType "Task"
              :input [{:valueCode "code-173329"}]})))))

(deftest library-test
  (testing "summary parsing"
    (satisfies-prop 20
      (prop/for-all [library (fg/library)]
        (let [source (write-cbor library)
              library (parse-cbor "Library" source :summary)]
          (and
           (->> library :meta :tag (some fu/subsetted?))
           (not-any? :data (:content library))))))))

(deftest consent-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [consent (fg/consent)]
          (= (->> (write-json consent)
                  (parse-json "Consent"))
             consent))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [consent (fg/consent)]
          (= (-> consent
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             consent))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [consent (fg/consent)]
          (= (->> (write-cbor consent)
                  (parse-cbor "Consent"))
             consent)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Consent" json))
        {:resourceType "Consent"
         :policyRule {}}
        {:fhir/type :fhir/Consent
         :policyRule #fhir/CodeableConcept{}})))

  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json
              {:fhir/type :fhir/Consent
               :policyRule #fhir/CodeableConcept{}})
             {:resourceType "Consent"
              :policyRule {}})))))
