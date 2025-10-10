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
   [blaze.fhir.spec.memory :as mem]
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
   [blaze.fhir.spec.type Base]
   [blaze.fhir.spec.type.system DateTimes Times]
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [com.google.common.hash Hashing]))

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

(defn- write-parse-cbor [type data]
  (parse-cbor type (j/write-value-as-bytes data cbor-object-mapper)))

(deftest resource-test
  (testing "valid"
    (are [x] (s2/valid? :fhir/Resource x)
      {:fhir/type :fhir/Condition :id "id-204446"
       :meta
       #fhir/Meta
        {:versionId #fhir/id "1"
         :profile [#fhir/canonical "url-164445"]}
       :code
       #fhir/CodeableConcept
        {:coding
         [#fhir/Coding
           {:system #fhir/uri "system-204435"
            :code #fhir/code "code-204441"}]}
       :subject #fhir/Reference{:reference #fhir/string "Patient/id-145552"}
       :onset #fhir/dateTime #system/date-time "2020-01-30"}

      {:fhir/type :fhir/Patient :id "0"
       :birthDate #fhir/date{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}})))

(deftest primitive-val-test
  (are [x] (fhir-spec/primitive-val? x)
    #fhir/string "foo"
    #fhir/integer 1
    #fhir/code "bar")

  (are [x] (not (fhir-spec/primitive-val? x))
    #fhir/Coding{}
    {:fhir/type :fhir.CodeSystem/concept}))

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
      "Observation"
      "Coding"))

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
  (testing "fails on missing type"
    (given (parse-json "")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Error on token null. Expected type is `Resource`.")

    (given (parse-json "{}")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JSON representation of a resource. Missing property `resourceType`."))

  (testing "fails on unsupported type"
    (given (parse-json "Foo" "")
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported type `Foo`.")

    (given (write-parse-json {:resourceType "Foo"})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Invalid JSON representation of a resource. Unsupported type `Foo`."))

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
        ::anom/message := "Invalid JSON representation of a resource. Error on integer value 0. Expected type is `string`."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 0. Expected type is `string`."
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
        ::anom/message := "Invalid JSON representation of a resource. Error on value `a`. Expected type is `date`. Can't parse `a` as System.Date because it doesn't has the right length."
        [:fhir/issues 0 :fhir.issues/code] := "invariant"
        [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `a`. Expected type is `date`. Can't parse `a` as System.Date because it doesn't has the right length."
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

  (testing "empty patient resource"
    (testing "gets type annotated"
      (given (write-parse-json {:resourceType "Patient"})
        :fhir/type := :fhir/Patient))

    (testing "stays the same"
      (is (= {:fhir/type :fhir/Patient}
             (write-parse-json {:resourceType "Patient"})))))

  (testing "deceasedBoolean on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased #fhir/boolean true}
           (write-parse-json {:resourceType "Patient" :deceasedBoolean true}))))

  (testing "deceasedDateTime on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased #fhir/dateTime #system/date-time "2020"}
           (write-parse-json {:resourceType "Patient" :deceasedDateTime "2020"}))))

  (testing "multipleBirthInteger on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :multipleBirth #fhir/integer 2}
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
                {:system #fhir/uri "http://loinc.org"
                 :code #fhir/code "39156-5"}]}}
           (write-parse-json
            {:resourceType "Observation"
             :code {:coding [{:system "http://loinc.org" :code "39156-5"}]}}))))

  (testing "Observation with valueTime"
    (is (= {:fhir/type :fhir/Observation
            :value #fhir/time #system/time "16:26:42"}
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
              :type #fhir/code "group"
              :item
              [{:fhir/type :fhir.Questionnaire/item
                :type #fhir/code "string"
                :text #fhir/string "foo"}]}]}
           (write-parse-json
            {:resourceType "Questionnaire"
             :item
             [{:type "group"
               :item
               [{:type "string"
                 :text "foo"}]}]}))))

  (testing "location resource with position"
    (is (= {:fhir/type :fhir/Location
            :position
            {:fhir/type :fhir.Location/position
             :latitude #fhir/decimal 0M
             :longitude #fhir/decimal 0M
             :altitude #fhir/decimal 0M}}
           (write-parse-json
            {:resourceType "Location"
             :position {:latitude 0
                        :longitude 0
                        :altitude 0}})))))

(deftest write-json-test
  (testing "without fhir type"
    (testing "at the root"
      (given (st/with-instrument-disabled (write-json {}))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing type."))

    (testing "backbone elements don't need types"
      (testing "cardinality single"
        (testing "no keys"
          (are [resource json] (= json (write-read-json resource))
            {:fhir/type :fhir.Bundle/entry :response {}}
            {:response {}}))

        (testing "one unknown key"
          (are [resource json] (= json (write-read-json resource))
            {:fhir/type :fhir/Bundle
             :entry [{:fhir/type :fhir.Bundle/entry :request {:foo "bar"}}]}
            {:resourceType "Bundle"
             :entry [{:request {}}]}))

        (testing "one known key"
          (are [resource json] (= json (write-read-json resource))
            {:fhir/type :fhir/Bundle
             :entry [{:fhir/type :fhir.Bundle/entry :request {:url #fhir/uri "bar"}}]}
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
           :codeFilter [{:searchParam #fhir/string "bar" :path #fhir/string "foo"}]}
          {:codeFilter [{:path "foo" :searchParam "bar"}]}))))

  (testing "with unsupported fhir type"
    (given (write-json {:fhir/type :fhir/Foo})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported type `Foo`."))

  (testing "Patient with deceasedBoolean"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Patient :deceased #fhir/boolean true}
      {:resourceType "Patient" :deceasedBoolean true}))

  (testing "Patient with deceasedDateTime"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Patient :deceased #fhir/dateTime #system/date-time "2020"}
      {:resourceType "Patient" :deceasedDateTime "2020"}))

  (testing "Patient with multipleBirthBoolean"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Patient :multipleBirth #fhir/boolean false}
      {:resourceType "Patient" :multipleBirthBoolean false}))

  (testing "Patient with multipleBirthInteger"
    (are [resource json] (= json (write-read-json resource))
      {:fhir/type :fhir/Patient :multipleBirth #fhir/integer 2}
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
           {:system #fhir/uri "http://loinc.org"
            :code #fhir/code "39156-5"}]}}
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
        {:value #fhir/decimal 36.6M
         :unit #fhir/string "kg/m^2"
         :system #fhir/uri "http://unitsofmeasure.org"
         :code #fhir/code "kg/m2"}}
      {:resourceType "Observation"
       :valueQuantity
       {:value 36.6
        :unit "kg/m^2"
        :system "http://unitsofmeasure.org"
        :code "kg/m2"}})))

(deftest parse-cbor-test
  (testing "fails on unsupported type"
    (given (parse-cbor "Foo" (byte-array 0))
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported type `Foo`."))

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
  (testing "without resource type"
    (given (st/with-instrument-disabled (write-cbor {}))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing type."))

  (testing "with unsupported type"
    (given (write-cbor {:fhir/type :fhir/Foo})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported type `Foo`."))

  (let [write-parse-cbor
        (fn [{:fhir/keys [type] :as resource}]
          (parse-cbor (name type) (write-cbor resource)))]

    (testing "Patient with deceasedBoolean"
      (are [resource] (= resource (write-parse-cbor resource))
        {:fhir/type :fhir/Patient :deceased #fhir/boolean true}))

    (testing "Patient with deceasedDateTime"
      (are [resource] (= resource (write-parse-cbor resource))
        {:fhir/type :fhir/Patient :deceased #fhir/dateTime #system/date-time "2020"}))

    (testing "Patient with multipleBirthBoolean"
      (are [resource] (= resource (write-parse-cbor resource))
        {:fhir/type :fhir/Patient :multipleBirth #fhir/boolean false}))

    (testing "Patient with multipleBirthInteger"
      (are [resource] (= resource (write-parse-cbor resource))
        {:fhir/type :fhir/Patient :multipleBirth #fhir/integer 2}))

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
             {:system #fhir/uri "http://loinc.org"
              :code #fhir/code "39156-5"}]}}))

    (testing "Observation with valueQuantity"
      (are [resource] (= resource (write-parse-cbor resource))
        {:fhir/type :fhir/Observation
         :value
         #fhir/Quantity
          {:value #fhir/decimal 36.6M
           :unit #fhir/string "kg/m^2"
           :system #fhir/uri "http://unitsofmeasure.org"
           :code #fhir/code "kg/m2"}}))

    (testing "Observation with valueTime"
      (are [resource] (= resource (write-parse-cbor resource))
        {:fhir/type :fhir/Observation
         :value #fhir/time #system/time "00:00"}))

    (testing "Observation with valueTime with id"
      (are [resource] (= resource (write-parse-cbor resource))
        {:fhir/type :fhir/Observation
         :value #fhir/time{:id "foo" :value #system/time"00:00"}}))))

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

  (testing "empty patient resource"
    (testing "gets type annotated"
      (given (conform-xml [::f/Patient])
        :fhir/type := :fhir/Patient))

    (testing "stays the same"
      (is (= {:fhir/type :fhir/Patient}
             (conform-xml [::f/Patient])))))

  (testing "patient resource with id"
    (is (= {:fhir/type :fhir/Patient :id "0"}
           (conform-xml [::f/Patient [::f/id {:value "0"}]]))))

  (testing "deceasedBoolean on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased #fhir/boolean true}
           (conform-xml [::f/Patient [::f/deceasedBoolean {:value "true"}]]))))

  (testing "deceasedDateTime on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased #fhir/dateTime #system/date-time "2020"}
           (conform-xml [::f/Patient [::f/deceasedDateTime {:value "2020"}]]))))

  (testing "multipleBirthInteger on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :multipleBirth #fhir/integer 2}
           (conform-xml [::f/Patient [::f/multipleBirthInteger {:value "2"}]]))))

  (testing "Observation with code"
    (is (= {:fhir/type :fhir/Observation
            :code
            #fhir/CodeableConcept
             {:coding
              [#fhir/Coding
                {:system #fhir/uri "http://loinc.org"
                 :code #fhir/code "39156-5"}]}}
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
                  {:system #fhir/uri "http://fhir.de/CodeSystem/gender-amtlich-de"
                   :code #fhir/code "D"
                   :display #fhir/string "divers"}}]
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
              :type #fhir/code "group"
              :item
              [{:fhir/type :fhir.Questionnaire/item
                :type #fhir/code "string"
                :text #fhir/string "foo"}]}]}
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
                   {:system #fhir/uri "http://fhir.de/CodeSystem/gender-amtlich-de"
                    :code #fhir/code "D"
                    :display #fhir/string "divers"}}]
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
      "Unknown resource type `<unknown>`.")))

;; ---- Primitive Types -------------------------------------------------------

(defn- emit [element]
  (xml/emit-str (assoc element :tag :foo)))

(deftest fhir-boolean-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 500
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
        (satisfies-prop 500
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

(deftest ^:mem-size fhir-boolean-mem-size-test
  (are [value size] (= size (Base/memSize value) (mem/total-size value))
    #fhir/boolean{:id "id-205204"} 88))

(deftest fhir-integer-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/integer-value]
            (= (sexp-value (str value)) (s2/unform :fhir.xml/integer (type/integer value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (testing "strings SHOULD NOT contain control chars - but can"
          (are [value] (= (type/string value) (s2/conform :fhir.xml/string (sexp-value value)))
            "\u001e"
            "\u0080"
            "\u0081"))

        (satisfies-prop 500
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
                               character-content [::f/extension {:url extension-url}]])))))))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 500
          (prop/for-all [value fg/string-value]
            (= (sexp-value value) (s2/unform :fhir.xml/string (type/string value)))))

        (testing "emit"
          (are [in out] (= (emit (s2/unform :fhir.xml/string (type/string in)))
                           (format "<?xml version='1.0' encoding='UTF-8'?><foo value=\"%s\"/>" out))
            "\u0000" "?"
            "\t" "&#x9;"
            "\n" "&#xa;"
            "\u000b" "?"
            "\u000c" "?"
            "\r" "&#xd;"
            "\u001e" "?"
            "\u001f" "?")

          (doseq [value ["\u007f" "\u0080" "\u0081"]]
            (is (= (emit (s2/unform :fhir.xml/string (type/string value)))
                   (format "<?xml version='1.0' encoding='UTF-8'?><foo value=\"%s\"/>" value))))

          (satisfies-prop 500
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

(deftest ^:mem-size fhir-string-mem-size-test
  (satisfies-prop 20
    (prop/for-all [string (fg/string :value fg/large-string-value)]
      (pos? (Base/memSize string))))

  (testing "interning"
    (are [x y] (zero? (mem/total-size* x y))
      #fhir/string "1234"
      #fhir/string "1234"))

  (testing "examples"
    (are [x size] (= (mem/total-size x) (Base/memSize x) size)
      #fhir/string "string-131125" 72)))

(deftest fhir-decimal-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/decimal-value]
            (= (sexp-value (str value)) (s2/unform :fhir.xml/decimal (type/decimal value))))))

      (testing "with extension"
        (satisfies-prop 500
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
        (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/uri-value]
            (= (sexp-value value) (s2/unform :fhir.xml/uri (type/uri value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (satisfies-prop 500
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
          "\u001e"
          "\u0081"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 500
          (prop/for-all [value fg/url-value]
            (= (sexp-value value) (s2/unform :fhir.xml/url (type/url value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (satisfies-prop 500
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
          "\u001e"
          "\u0081"))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 500
          (prop/for-all [value fg/canonical-value]
            (= (sexp-value value) (s2/unform :fhir.xml/canonical (type/canonical value)))))

        (testing "emit"
          (satisfies-prop 500
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

(deftest ^:mem-size fhir-canonical-mem-size-test
  (satisfies-prop 10
    (prop/for-all [value (fg/canonical :id (gen/return nil) :extension (gen/return nil))]
      (and (zero? (Base/memSize value))
           (= (mem/total-size value) (mem/total-size value value))))))

(deftest fhir-base64Binary-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/base64Binary-value]
            (= (sexp-value value) (s2/unform :fhir.xml/base64Binary (type/base64Binary value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/instant-value]
            (= (type/instant value)
               (s2/conform :fhir.xml/instant (sexp-value (DateTimes/toString value))))))

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
                              [nil (cond-> {} id (assoc :id id) value (assoc :value (DateTimes/toString value)))
                               [::f/extension {:url extension-url}]]))))))

        (testing "invalid"
          (are [v] (s2/invalid? (s2/conform :fhir.xml/instant (sexp-value v)))
            "2019-13"
            "2019-02-29")))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 500
          (prop/for-all [value fg/instant-value]
            (= (sexp-value (DateTimes/toString value))
               (s2/unform :fhir.xml/instant (type/instant value)))))

        (testing "emit"
          (satisfies-prop 500
            (prop/for-all [value fg/instant-value]
              (emit (s2/unform :fhir.xml/instant (type/instant value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/instant-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value (DateTimes/toString value)))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/instant
                          (type/instant {:id id
                                         :extension
                                         [(type/extension {:url extension-url})]
                                         :value value})))))))))

(deftest ^:mem-size fhir-instant-mem-size-test
  (satisfies-prop 100
    (prop/for-all [value (fg/instant :value fg/instant-value)]
      (pos? (Base/memSize value)))))

(deftest fhir-date-test
  (testing "valid"
    (are [x] (s2/valid? :fhir/date x)
      #fhir/date{:extension [#fhir/Extension{:url "foo" :value #fhir/code "bar"}]}))

  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 500
          (prop/for-all [value fg/date-value]
            (= (type/date value) (s2/conform :fhir.xml/date (sexp-value (str value))))))

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
                              [nil (cond-> {} id (assoc :id id) value (assoc :value (str value)))
                               character-content [::f/extension {:url extension-url}]])))))))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 500
          (prop/for-all [value fg/date-value]
            (= (sexp-value (str value)) (s2/unform :fhir.xml/date (type/date value)))))

        (testing "emit"
          (satisfies-prop 500
            (prop/for-all [value fg/date-value]
              (emit (s2/unform :fhir.xml/date (type/date value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/date-value (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value (str value)))
                 [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/date
                          (type/date {:id id
                                      :extension
                                      [(type/extension {:url extension-url})]
                                      :value value})))))))))

(deftest ^:mem-size fhir-date-mem-size-test
  (satisfies-prop 100
    (prop/for-all [value (fg/date :value fg/date-value)]
      (pos? (Base/memSize value)))))

(deftest fhir-dateTime-test
  (testing "parsing"
    (testing "XML"
      (testing "valid"
        (satisfies-prop 500
          (prop/for-all [value (fg/dateTime-value)]
            (= (type/dateTime value) (s2/conform :fhir.xml/dateTime (sexp-value (DateTimes/toString value))))))

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
                              [nil (cond-> {} id (assoc :id id) value (assoc :value (DateTimes/toString value)))
                               [::f/extension {:url extension-url}]])))))))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 500
          (prop/for-all [value (fg/dateTime-value)]
            (= (sexp-value (DateTimes/toString value)) (s2/unform :fhir.xml/dateTime (type/dateTime value)))))

        (testing "emit"
          (satisfies-prop 500
            (prop/for-all [value (fg/dateTime-value)]
              (emit (s2/unform :fhir.xml/dateTime (type/dateTime value)))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [(fg/dateTime-value) (gen/return nil)])]
            (= (sexp
                [nil (cond-> {} id (assoc :id id) value (assoc :value (DateTimes/toString value)))
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
        (satisfies-prop 500
          (prop/for-all [value fg/time-value]
            (= (type/time value) (s2/conform :fhir.xml/time (sexp-value (Times/toString value))))))

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
                              [nil (cond-> {} id (assoc :id id) value (assoc :value (Times/toString value)))
                               [::f/extension {:url extension-url}]]))))))

        (testing "invalid"
          (are [v] (s2/invalid? (s2/conform :fhir.xml/time (sexp-value v)))
            "24:00"
            "24:00:00")))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 500
          (prop/for-all [value fg/time-value]
            (= (sexp-value (Times/toString value)) (s2/unform :fhir.xml/time (type/time value)))))

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
                [nil (cond-> {} id (assoc :id id) value (assoc :value (Times/toString value)))
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
        (are [value] (= (type/code value) (s2/conform :fhir.xml/code (sexp-value value)))
          "a\tb"
          "a\u00a0b")

        (satisfies-prop 500
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
                               [::f/extension {:url extension-url}]])))))))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 500
          (prop/for-all [value fg/code-value]
            (= (sexp-value value) (s2/unform :fhir.xml/code (type/code value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/oid-value]
            (= (sexp-value value) (s2/unform :fhir.xml/oid (type/oid value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/id-value]
            (= (sexp-value value) (s2/unform :fhir.xml/id (type/id value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (testing "markdown strings SHOULD NOT contain control chars - but can"
          (are [value] (= (type/markdown value) (s2/conform :fhir.xml/markdown (sexp-value value)))
            "\u001e"
            "\u0080"
            "\u0081"))

        (satisfies-prop 500
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
                               [::f/extension {:url extension-url}]])))))))))

  (testing "writing"
    (testing "XML"
      (testing "value only"
        (satisfies-prop 500
          (prop/for-all [value fg/markdown-value]
            (= (sexp-value value) (s2/unform :fhir.xml/markdown (type/markdown value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/unsignedInt-value]
            (= (sexp-value value) (s2/unform :fhir.xml/unsignedInt (type/unsignedInt value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/positiveInt-value]
            (= (sexp-value value) (s2/unform :fhir.xml/positiveInt (type/positiveInt value)))))

        (testing "emit"
          (satisfies-prop 500
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
        (satisfies-prop 500
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
        (satisfies-prop 500
          (prop/for-all [value fg/uuid-value]
            (= (sexp-value value) (s2/unform :fhir.xml/uuid (type/uuid value)))))

        (testing "emit"
          (satisfies-prop 500
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
      #fhir/xhtml "<div xmlns=\"http://www.w3.org/1999/xhtml\"></div>"))

  (testing "parsing"
    (testing "XML"
      (is (= #fhir/xhtml "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"
             (s2/conform :fhir.xml/xhtml xhtml-element)))))

  (testing "writing"
    (testing "XML"
      (is (= xhtml-element
             (s2/unform :fhir.xml/xhtml #fhir/xhtml "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"))))))

;; ---- Complex Types ---------------------------------------------------------

(defmacro mem-size-test [type & body]
  `(are [x# size#] (= (Base/memSize (write-parse-json ~type x#))
                      (mem/total-size* (write-parse-json ~type x#)
                                       (write-parse-json ~type x#))
                      size#)
     ~@body))

(deftest address-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/address)]
          (s2/valid? :fhir/Address x)))))

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
        #fhir/Address{:use #fhir/code "usual"})

      (testing "invalid"
        (given (write-parse-json "Address" {:use 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/expression] := "Address.use"))

      (testing "interned data elements"
        (are [json key value] (identical? value (key (write-parse-json "Address" json)))
          {:city "141600"} :city #fhir/string-interned "141600"
          {:district "141612"} :district #fhir/string-interned "141612"
          {:state "141621"} :state #fhir/string-interned "141621"
          {:postalCode "141631"} :postalCode #fhir/string-interned "141631"
          {:country "141643"} :country #fhir/string-interned "141643")))

    (testing "XML"
      (testing "interned data elements"
        (are [xml key value] (identical? value (key (s2/conform :fhir.xml/Address xml)))
          (sexp [nil {} [:city {:value "145938"}]]) :city #fhir/string-interned "145938"
          (sexp [nil {} [:district {:value "145938"}]]) :district #fhir/string-interned "145938"
          (sexp [nil {} [:state {:value "145938"}]]) :state #fhir/string-interned "145938"
          (sexp [nil {} [:postalCode {:value "145938"}]]) :postalCode #fhir/string-interned "145938"
          (sexp [nil {} [:country {:value "150034"}]]) :country #fhir/string-interned "150034"))))

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

        #fhir/Address{:use #fhir/code "use-155449"}
        {:use "use-155449"}

        #fhir/Address{:text #fhir/string "text-170345"}
        {:text "text-170345"}

        #fhir/Address{:line [#fhir/string "line-171433"]}
        {:line ["line-171433"]}

        #fhir/Address{:line [#fhir/string "line-171433" #fhir/string "line-171857"]}
        {:line ["line-171433" "line-171857"]}

        #fhir/Address{:city #fhir/string "city-171937"}
        {:city "city-171937"}

        #fhir/Address{:district #fhir/string "district-171937"}
        {:district "district-171937"}

        #fhir/Address{:state #fhir/string "state-171937"}
        {:state "state-171937"}

        #fhir/Address{:postalCode #fhir/string "postalCode-171937"}
        {:postalCode "postalCode-171937"}

        #fhir/Address{:country #fhir/string "country-171937"}
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

        #fhir/Address{:use #fhir/code "use-155449"}
        (sexp [nil {} [::f/use {:value "use-155449"}]])

        #fhir/Address{:text #fhir/string "text-170345"}
        (sexp [nil {} [::f/text {:value "text-170345"}]])

        #fhir/Address{:line [#fhir/string "line-171433"]}
        (sexp [nil {} [::f/line {:value "line-171433"}]])

        #fhir/Address{:line [#fhir/string "line-171433" #fhir/string "line-171857"]}
        (sexp [nil {}
               [::f/line {:value "line-171433"}]
               [::f/line {:value "line-171857"}]])

        #fhir/Address{:city #fhir/string "city-171937"}
        (sexp [nil {} [::f/city {:value "city-171937"}]])

        #fhir/Address{:district #fhir/string "district-171937"}
        (sexp [nil {} [::f/district {:value "district-171937"}]])

        #fhir/Address{:state #fhir/string "state-171937"}
        (sexp [nil {} [::f/state {:value "state-171937"}]])

        #fhir/Address{:postalCode #fhir/string "postalCode-171937"}
        (sexp [nil {} [::f/postalCode {:value "postalCode-171937"}]])

        #fhir/Address{:country #fhir/string "country-171937"}
        (sexp [nil {} [::f/country {:value "country-171937"}]])

        #fhir/Address{:period #fhir/Period{}}
        (sexp [nil {} [::f/period]])))))

(deftest ^:mem-size address-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/address))]
      (>= (Base/memSize (parse-cbor "Address" source))
          (mem/total-size* (parse-cbor "Address" source)
                           (parse-cbor "Address" source)))))

  (testing "examples"
    (mem-size-test "Address"
      {:text "text-171612"} 120)))

(deftest age-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/age)]
          (s2/valid? :fhir/Age x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/age)]
          (= (->> (write-json x)
                  (parse-json "Age"))
             (->> (write-json x)
                  (parse-json "Age")
                  (write-json)
                  (parse-json "Age"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/age)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Age))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/age)]
          (= (->> (write-cbor x)
                  (parse-cbor "Age"))
             (->> (write-cbor x)
                  (parse-cbor "Age")
                  (write-cbor)
                  (parse-cbor "Age"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Age" json))
        {}
        #fhir/Age{}

        {:value 1M}
        #fhir/Age{:value #fhir/decimal 1M})

      (testing "invalid"
        (given (write-parse-json "Age" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Age.value"))

      (testing "interned data elements"
        (are [json key value] (identical? value (key (write-parse-json "Age" json)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641")))

    (testing "XML"
      (testing "interned data elements"
        (are [xml key value] (identical? value (key (s2/conform :fhir.xml/Age xml)))
          (sexp [nil {} [:unit {:value "151938"}]]) :unit #fhir/string-interned "151938"
          (sexp [nil {} [:system {:value "151921"}]]) :system #fhir/uri-interned "151921")))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "Age" json))
        {}
        #fhir/Age{}

        {:value 1M}
        #fhir/Age{:value #fhir/decimal 1M})

      (testing "interned data elements"
        (are [cbor key value] (identical? value (key (write-parse-cbor "Age" cbor)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641"))

      (testing "interning works"
        (are [x y] (identical? x y)
          (write-parse-cbor "Age" {:unit "unit-183017"})
          (write-parse-cbor "Age" {:unit "unit-183017"})

          (write-parse-cbor "Age" {:system "system-151829"})
          (write-parse-cbor "Age" {:system "system-151829"})))

      (testing "invalid"
        (given (write-parse-cbor "Age" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Age.value"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Age{}
        {}

        #fhir/Age{:id "id-134908"}
        {:id "id-134908"}

        #fhir/Age{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Age{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Age{:value #fhir/decimal 1M}
        {:value 1}

        #fhir/Age{:comparator #fhir/code "code-153342"}
        {:comparator "code-153342"}

        #fhir/Age{:unit #fhir/string "string-153351"}
        {:unit "string-153351"}

        #fhir/Age{:system #fhir/uri "system-153337"}
        {:system "system-153337"}

        #fhir/Age{:code #fhir/code "code-153427"}
        {:code "code-153427"}))))

(deftest ^:mem-size age-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/age))]
      (>= (Base/memSize (parse-cbor "Age" source))
          (mem/total-size* (parse-cbor "Age" source)
                           (parse-cbor "Age" source)))))

  (testing "examples"
    (mem-size-test "Age"
      {:value 11} 80)))

(deftest annotation-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/annotation)]
          (s2/valid? :fhir/Annotation x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/annotation)]
          (= (->> (write-json x)
                  (parse-json "Annotation"))
             (->> (write-json x)
                  (parse-json "Annotation")
                  (write-json)
                  (parse-json "Annotation"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/annotation)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Annotation))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/annotation)]
          (= (->> (write-cbor x)
                  (parse-cbor "Annotation"))
             (->> (write-cbor x)
                  (parse-cbor "Annotation")
                  (write-cbor)
                  (parse-cbor "Annotation"))
             x))))))

(deftest ^:mem-size annotation-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/annotation))]
      (>= (Base/memSize (parse-cbor "Annotation" source))
          (mem/total-size* (parse-cbor "Annotation" source)
                           (parse-cbor "Annotation" source)))))

  (testing "examples"
    (mem-size-test "Annotation"
      {:text "text-174325"} 88)))

(deftest attachment-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/attachment)]
          (s2/valid? :fhir/Attachment x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/attachment)]
          (= (->> (write-json x)
                  (parse-json "Attachment"))
             (->> (write-json x)
                  (parse-json "Attachment")
                  (write-json)
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
             (->> (write-cbor x)
                  (parse-cbor "Attachment")
                  (write-cbor)
                  (parse-cbor "Attachment"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Attachment" json))
        {}
        #fhir/Attachment{}

        {:contentType "code-115735"}
        #fhir/Attachment{:contentType #fhir/code "code-115735"}

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
        #fhir/Attachment{:data #fhir/base64Binary "MTA1NjE0Cg=="}

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
        #fhir/Attachment{:contentType #fhir/code "code-115735"}

        (sexp [::f/Attachment {} [::f/contentType {:value "code-115735"} [::f/extension {:url "url-101510"}]]])
        #fhir/Attachment{:contentType #fhir/code{:value "code-115735" :extension [#fhir/Extension{:url "url-101510"}]}}

        (sexp [::f/Attachment {} [::f/contentType {:id "id-205332"}]])
        #fhir/Attachment{:contentType #fhir/code{:id "id-205332"}}

        (sexp [::f/Attachment {} [::f/contentType {} [::f/extension {:url "url-101510"}]]])
        #fhir/Attachment{:contentType #fhir/code{:extension [#fhir/Extension{:url "url-101510"}]}}

        (sexp [::f/Attachment {} [::f/data {:value "MTA1NjE0Cg=="}]])
        #fhir/Attachment{:data #fhir/base64Binary "MTA1NjE0Cg=="}

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

        #fhir/Attachment{:contentType #fhir/code "code-150209"}
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

        #fhir/Attachment{:language #fhir/code "de"}
        {:language "de"}

        #fhir/Attachment{:data #fhir/base64Binary "MTA1NjE0Cg=="}
        {:data "MTA1NjE0Cg=="}

        #fhir/Attachment{:data #fhir/base64Binary{:extension [#fhir/Extension{:url "url-115417"}]}}
        {:_data {:extension [{:url "url-115417"}]}}

        #fhir/Attachment{:data #fhir/base64Binary{:extension [#fhir/Extension{}] :value "MTA1NjE0Cg=="}}
        {:data "MTA1NjE0Cg=="
         :_data {:extension [{}]}}

        #fhir/Attachment{:url #fhir/url "url-210424"}
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

        #fhir/Attachment{:hash #fhir/base64Binary "MTA1NjE0Cg=="}
        {:hash "MTA1NjE0Cg=="}

        #fhir/Attachment{:title #fhir/string "title-210622"}
        {:title "title-210622"}

        #fhir/Attachment{:creation #fhir/dateTime{:extension [#fhir/Extension{:url "url-132312"}]}}
        {:_creation {:extension [{:url "url-132312"}]}}

        #fhir/Attachment{:creation #fhir/dateTime{:extension [#fhir/Extension{:url "url-132333"}] :value #system/date-time "2022"}}
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

        #fhir/Attachment{:contentType #fhir/code "code-150209"}
        (sexp [nil {} [::f/contentType {:value "code-150209"}]])

        #fhir/Attachment{:contentType #fhir/code{:extension [#fhir/Extension{}]}}
        (sexp [nil {} [::f/contentType {} [::f/extension]]])

        #fhir/Attachment{:contentType #fhir/code{:id "id-205332"}}
        (sexp [nil {} [::f/contentType {:id "id-205332"}]])

        #fhir/Attachment{:contentType #fhir/code{:extension [#fhir/Extension{}] :value "code-150225"}}
        (sexp [nil {} [::f/contentType {:value "code-150225"} [::f/extension]]])

        #fhir/Attachment{:contentType #fhir/code{:id "id-205544" :extension [#fhir/Extension{}] :value "code-150225"}}
        (sexp [nil {} [::f/contentType {:id "id-205544" :value "code-150225"} [::f/extension]]])

        #fhir/Attachment{:language #fhir/code "de"}
        (sexp [nil {} [::f/language {:value "de"}]])

        #fhir/Attachment{:data #fhir/base64Binary "MTA1NjE0Cg=="}
        (sexp [nil {} [::f/data {:value "MTA1NjE0Cg=="}]])

        #fhir/Attachment{:data #fhir/base64Binary{:extension [#fhir/Extension{:url "url-115417"}]}}
        (sexp [nil {} [::f/data {} [::f/extension {:url "url-115417"}]]])

        #fhir/Attachment{:url #fhir/url "url-210424"}
        (sexp [nil {} [::f/url {:value "url-210424"}]])

        #fhir/Attachment{:url #fhir/url{:extension [#fhir/Extension{:url "url-130143"}]}}
        (sexp [nil {} [::f/url {} [::f/extension {:url "url-130143"}]]])

        #fhir/Attachment{:size #fhir/unsignedInt 204742}
        (sexp [nil {} [::f/size {:value "204742"}]])

        #fhir/Attachment{:size #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-130946"}]}}
        (sexp [nil {} [::f/size {} [::f/extension {:url "url-130946"}]]])

        #fhir/Attachment{:hash #fhir/base64Binary "MTA1NjE0Cg=="}
        (sexp [nil {} [::f/hash {:value "MTA1NjE0Cg=="}]])

        #fhir/Attachment{:title #fhir/string "title-210622"}
        (sexp [nil {} [::f/title {:value "title-210622"}]])

        #fhir/Attachment{:creation #fhir/dateTime #system/date-time "2021"}
        (sexp [nil {} [::f/creation {:value "2021"}]]))))

  (testing "summary parsing"
    (satisfies-prop 20
      (prop/for-all [attachment (fg/attachment)]
        (let [source (write-cbor attachment)
              attachment (parse-cbor "Attachment" source :summary)]
          (nil? (:data attachment)))))))

(deftest ^:mem-size attachment-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/attachment))]
      (>= (Base/memSize (parse-cbor "Attachment" source))
          (mem/total-size* (parse-cbor "Attachment" source)
                           (parse-cbor "Attachment" source)))))

  (testing "examples"
    (mem-size-test "Attachment"
      {:hash "MTA1NjE0Cg=="} 112)))

(deftest codeable-concept-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept)]
          (s2/valid? :fhir/CodeableConcept x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept)]
          (= (->> (write-json x)
                  (parse-json "CodeableConcept"))
             (->> (write-json x)
                  (parse-json "CodeableConcept")
                  (write-json)
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
             (->> (write-cbor x)
                  (parse-cbor "CodeableConcept")
                  (write-cbor)
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
        #fhir/CodeableConcept{:text #fhir/string "text-223528"})

      (testing "interned data elements"
        (are [json key value] (identical? value (key (write-parse-json "CodeableConcept" json)))
          {:text "143903"} :text #fhir/string-interned "143903")))

    (testing "XML"
      (testing "interned data elements"
        (are [xml key value] (identical? value (key (s2/conform :fhir.xml/CodeableConcept xml)))
          (sexp [nil {} [:text {:value "150413"}]]) :text #fhir/string-interned "150413")))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "CodeableConcept" json))
        {}
        #fhir/CodeableConcept{}
        {:coding [{}]}
        #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
        {:coding [{:system "foo" :code "bar"}]}
        #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"}]}
        {:text "text-223528"}
        #fhir/CodeableConcept{:text #fhir/string "text-223528"})

      (testing "interned data elements"
        (are [cbor key value] (identical? value (key (write-parse-cbor "CodeableConcept" cbor)))
          {:text "143903"} :text #fhir/string-interned "143903"))

      (testing "interning works"
        (are [x y] (identical? x y)
          (write-parse-cbor "CodeableConcept" {:text "text-182912"})
          (write-parse-cbor "CodeableConcept" {:text "text-182912"})))))

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

        #fhir/CodeableConcept{:text #fhir/string "text-223528"}
        {:text "text-223528"}))))

(deftest ^:mem-size codeable-concept-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/codeable-concept))]
      (>= (Base/memSize (parse-cbor "CodeableConcept" source))
          (mem/total-size* (parse-cbor "CodeableConcept" source)
                           (parse-cbor "CodeableConcept" source)))))

  (testing "interning"
    (are [x] (zero? (mem/total-size* (write-parse-json "CodeableConcept" x)
                                     (write-parse-json "CodeableConcept" x)))
      {:coding
       [{:system "http://snomed.info/sct"
         :code "160903007"
         :display "Full-time employment (finding)"}]}

      {:coding
       [{:system "http://snomed.info/sct"
         :code "160903007"
         :display "Full-time employment (finding)"}]
       :text "Full-time employment (finding)"}))

  (testing "examples"
    (mem-size-test "CodeableConcept"
      {:id "id-173830"} 96)))

(deftest coding-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding)]
          (s2/valid? :fhir/Coding x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/coding)]
          (= (->> (write-json x)
                  (parse-json "Coding"))
             (->> (write-json x)
                  (parse-json "Coding")
                  (write-json)
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
             (->> (write-cbor x)
                  (parse-cbor "Coding")
                  (write-cbor)
                  (parse-cbor "Coding"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Coding" json))
        {:system "foo" :code "bar"}
        #fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"}
        {:display "foo"}
        #fhir/Coding{:display #fhir/string "foo"})

      (testing "interned data elements"
        (are [json key value] (identical? value (key (write-parse-json "Coding" json)))
          {:system "143123"} :system #fhir/uri-interned "143123"
          {:version "143903"} :version #fhir/string-interned "143903"
          {:display "143942"} :display #fhir/string-interned "143942")))

    (testing "XML"
      (testing "interned data elements"
        (are [xml key value] (identical? value (key (s2/conform :fhir.xml/Coding xml)))
          (sexp [nil {} [:system {:value "145550"}]]) :system #fhir/uri-interned "145550"
          (sexp [nil {} [:version {:value "145938"}]]) :version #fhir/string-interned "145938"
          (sexp [nil {} [:display {:value "150034"}]]) :display #fhir/string-interned "150034")))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "Coding" json))
        {:system "foo" :code "bar"}
        #fhir/Coding{:system #fhir/uri "foo" :code #fhir/code "bar"})

      (testing "interned data elements"
        (are [cbor key value] (identical? value (key (write-parse-cbor "Coding" cbor)))
          {:system "143123"} :system #fhir/uri-interned "143123"
          {:version "143903"} :version #fhir/string-interned "143903"
          {:display "143942"} :display #fhir/string-interned "143942"))

      (testing "interning works"
        (are [x y] (identical? x y)
          (write-parse-cbor "Coding" {:system "system-143620" :code "bar"})
          (write-parse-cbor "Coding" {:system "system-143620" :code "bar"})

          (write-parse-cbor "Coding" {:system "system-143620" :version "version-182229" :code "bar"})
          (write-parse-cbor "Coding" {:system "system-143620" :version "version-182229" :code "bar"})

          (write-parse-cbor "Coding" {:system "system-143620" :code "bar" :display "display-182103"})
          (write-parse-cbor "Coding" {:system "system-143620" :code "bar" :display "display-182103"})))))

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

        #fhir/Coding{:system #fhir/uri "system-185812"}
        {:system "system-185812"}

        #fhir/Coding{:version #fhir/string "version-185951"}
        {:version "version-185951"}

        #fhir/Coding{:code #fhir/code "code-190226"}
        {:code "code-190226"}

        #fhir/Coding{:display #fhir/string "display-190327"}
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

        #fhir/Coding{:system #fhir/uri "system-185812"}
        (sexp [nil {} [::f/system {:value "system-185812"}]])

        #fhir/Coding{:version #fhir/string "version-185951"}
        (sexp [nil {} [::f/version {:value "version-185951"}]])

        #fhir/Coding{:code #fhir/code "code-190226"}
        (sexp [nil {} [::f/code {:value "code-190226"}]])

        #fhir/Coding{:display #fhir/string "display-190327"}
        (sexp [nil {} [::f/display {:value "display-190327"}]])))))

(deftest ^:mem-size coding-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/coding))]
      (>= (Base/memSize (parse-cbor "Coding" source))
          (mem/total-size* (parse-cbor "Coding" source)
                           (parse-cbor "Coding" source)))))

  (testing "interning"
    (are [x] (zero? (mem/total-size* (write-parse-json "Coding" x)
                                     (write-parse-json "Coding" x)))
      {:system "http://snomed.info/sct"
       :code "160903007"
       :display "Full-time employment (finding)"}))

  (testing "examples"
    (mem-size-test "Coding"
      {:id "id-173830"} 112)))

(deftest contact-detail-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/contact-detail)]
          (s2/valid? :fhir/ContactDetail x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/contact-detail)]
          (= (->> (write-json x)
                  (parse-json "ContactDetail"))
             (->> (write-json x)
                  (parse-json "ContactDetail")
                  (write-json)
                  (parse-json "ContactDetail"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/contact-detail)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/ContactDetail))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/contact-detail)]
          (= (->> (write-cbor x)
                  (parse-cbor "ContactDetail"))
             (->> (write-cbor x)
                  (parse-cbor "ContactDetail")
                  (write-cbor)
                  (parse-cbor "ContactDetail"))
             x))))))

(deftest ^:mem-size contact-detail-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/contact-detail))]
      (>= (Base/memSize (parse-cbor "ContactDetail" source))
          (mem/total-size* (parse-cbor "ContactDetail" source)
                           (parse-cbor "ContactDetail" source)))))

  (testing "examples"
    (mem-size-test "ContactDetail"
      {:name "string-131125"} 96)))

(deftest contact-point-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/contact-point)]
          (s2/valid? :fhir/ContactPoint x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/contact-point)]
          (= (->> (write-json x)
                  (parse-json "ContactPoint"))
             (->> (write-json x)
                  (parse-json "ContactPoint")
                  (write-json)
                  (parse-json "ContactPoint"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/contact-point)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/ContactPoint))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/contact-point)]
          (= (->> (write-cbor x)
                  (parse-cbor "ContactPoint"))
             (->> (write-cbor x)
                  (parse-cbor "ContactPoint")
                  (write-cbor)
                  (parse-cbor "ContactPoint"))
             x))))))

(deftest ^:mem-size contact-point-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/contact-point))]
      (>= (Base/memSize (parse-cbor "ContactPoint" source))
          (mem/total-size* (parse-cbor "ContactPoint" source)
                           (parse-cbor "ContactPoint" source)))))

  (testing "examples"
    (mem-size-test "ContactPoint"
      {:value "string-131125"} 104)))

(deftest contributor-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/contributor)]
          (s2/valid? :fhir/Contributor x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/contributor)]
          (= (->> (write-json x)
                  (parse-json "Contributor"))
             (->> (write-json x)
                  (parse-json "Contributor")
                  (write-json)
                  (parse-json "Contributor"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/contributor)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Contributor))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/contributor)]
          (= (->> (write-cbor x)
                  (parse-cbor "Contributor"))
             (->> (write-cbor x)
                  (parse-cbor "Contributor")
                  (write-cbor)
                  (parse-cbor "Contributor"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Contributor" json))
        {}
        #fhir/Contributor{}

        {:type "foo"}
        #fhir/Contributor{:type #fhir/code "foo"})))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Contributor{}
        {}

        #fhir/Contributor{:id "id-134908"}
        {:id "id-134908"}

        #fhir/Contributor{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Contributor{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Contributor{:type #fhir/code "foo"}
        {:type "foo"}))))

(deftest ^:mem-size contributor-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/contributor))]
      (>= (Base/memSize (parse-cbor "Contributor" source))
          (mem/total-size* (parse-cbor "Contributor" source)
                           (parse-cbor "Contributor" source)))))

  (testing "examples"
    (mem-size-test "Contributor"
      {:type "foo"} 24)))

(deftest count-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/count)]
          (s2/valid? :fhir/Count x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/count)]
          (= (->> (write-json x)
                  (parse-json "Count"))
             (->> (write-json x)
                  (parse-json "Count")
                  (write-json)
                  (parse-json "Count"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/count)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Count))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/count)]
          (= (->> (write-cbor x)
                  (parse-cbor "Count"))
             (->> (write-cbor x)
                  (parse-cbor "Count")
                  (write-cbor)
                  (parse-cbor "Count"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Count" json))
        {}
        #fhir/Count{}

        {:value 1M}
        #fhir/Count{:value #fhir/decimal 1M})

      (testing "invalid"
        (given (write-parse-json "Count" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Count.value"))

      (testing "interned data elements"
        (are [json key value] (identical? value (key (write-parse-json "Count" json)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641")))

    (testing "XML"
      (testing "interned data elements"
        (are [xml key value] (identical? value (key (s2/conform :fhir.xml/Count xml)))
          (sexp [nil {} [:unit {:value "151938"}]]) :unit #fhir/string-interned "151938"
          (sexp [nil {} [:system {:value "151921"}]]) :system #fhir/uri-interned "151921")))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "Count" json))
        {}
        #fhir/Count{}

        {:value 1M}
        #fhir/Count{:value #fhir/decimal 1M})

      (testing "interned data elements"
        (are [cbor key value] (identical? value (key (write-parse-cbor "Count" cbor)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641"))

      (testing "interning works"
        (are [x y] (identical? x y)
          (write-parse-cbor "Count" {:unit "unit-183017"})
          (write-parse-cbor "Count" {:unit "unit-183017"})

          (write-parse-cbor "Count" {:system "system-151829"})
          (write-parse-cbor "Count" {:system "system-151829"})))

      (testing "invalid"
        (given (write-parse-cbor "Count" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Count.value"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Count{}
        {}

        #fhir/Count{:id "id-134908"}
        {:id "id-134908"}

        #fhir/Count{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Count{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Count{:value #fhir/decimal 1M}
        {:value 1}

        #fhir/Count{:comparator #fhir/code "code-153342"}
        {:comparator "code-153342"}

        #fhir/Count{:unit #fhir/string "string-153351"}
        {:unit "string-153351"}

        #fhir/Count{:system #fhir/uri "system-153337"}
        {:system "system-153337"}

        #fhir/Count{:code #fhir/code "code-153427"}
        {:code "code-153427"}))))

(deftest ^:mem-size count-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/count))]
      (>= (Base/memSize (parse-cbor "Count" source))
          (mem/total-size* (parse-cbor "Count" source)
                           (parse-cbor "Count" source)))))

  (testing "examples"
    (mem-size-test "Count"
      {:value 11} 80)))

(deftest distance-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/distance)]
          (s2/valid? :fhir/Distance x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/distance)]
          (= (->> (write-json x)
                  (parse-json "Distance"))
             (->> (write-json x)
                  (parse-json "Distance")
                  (write-json)
                  (parse-json "Distance"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/distance)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Distance))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/distance)]
          (= (->> (write-cbor x)
                  (parse-cbor "Distance"))
             (->> (write-cbor x)
                  (parse-cbor "Distance")
                  (write-cbor)
                  (parse-cbor "Distance"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Distance" json))
        {}
        #fhir/Distance{}

        {:value 1M}
        #fhir/Distance{:value #fhir/decimal 1M})

      (testing "invalid"
        (given (write-parse-json "Distance" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Distance.value"))

      (testing "interned data elements"
        (are [json key value] (identical? value (key (write-parse-json "Distance" json)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641")))

    (testing "XML"
      (testing "interned data elements"
        (are [xml key value] (identical? value (key (s2/conform :fhir.xml/Distance xml)))
          (sexp [nil {} [:unit {:value "151938"}]]) :unit #fhir/string-interned "151938"
          (sexp [nil {} [:system {:value "151921"}]]) :system #fhir/uri-interned "151921")))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "Distance" json))
        {}
        #fhir/Distance{}

        {:value 1M}
        #fhir/Distance{:value #fhir/decimal 1M})

      (testing "interned data elements"
        (are [cbor key value] (identical? value (key (write-parse-cbor "Distance" cbor)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641"))

      (testing "interning works"
        (are [x y] (identical? x y)
          (write-parse-cbor "Distance" {:unit "unit-183017"})
          (write-parse-cbor "Distance" {:unit "unit-183017"})

          (write-parse-cbor "Distance" {:system "system-151829"})
          (write-parse-cbor "Distance" {:system "system-151829"})))

      (testing "invalid"
        (given (write-parse-cbor "Distance" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Distance.value"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Distance{}
        {}

        #fhir/Distance{:id "id-134908"}
        {:id "id-134908"}

        #fhir/Distance{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Distance{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Distance{:value #fhir/decimal 1M}
        {:value 1}

        #fhir/Distance{:comparator #fhir/code "code-153342"}
        {:comparator "code-153342"}

        #fhir/Distance{:unit #fhir/string "string-153351"}
        {:unit "string-153351"}

        #fhir/Distance{:system #fhir/uri "system-153337"}
        {:system "system-153337"}

        #fhir/Distance{:code #fhir/code "code-153427"}
        {:code "code-153427"}))))

(deftest ^:mem-size distance-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/distance))]
      (>= (Base/memSize (parse-cbor "Distance" source))
          (mem/total-size* (parse-cbor "Distance" source)
                           (parse-cbor "Distance" source)))))

  (testing "examples"
    (mem-size-test "Distance"
      {:value 11} 80)))

(deftest duration-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/duration)]
          (s2/valid? :fhir/Duration x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/duration)]
          (= (->> (write-json x)
                  (parse-json "Duration"))
             (->> (write-json x)
                  (parse-json "Duration")
                  (write-json)
                  (parse-json "Duration"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/duration)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Duration))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/duration)]
          (= (->> (write-cbor x)
                  (parse-cbor "Duration"))
             (->> (write-cbor x)
                  (parse-cbor "Duration")
                  (write-cbor)
                  (parse-cbor "Duration"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Duration" json))
        {}
        #fhir/Duration{}

        {:value 1M}
        #fhir/Duration{:value #fhir/decimal 1M})

      (testing "invalid"
        (given (write-parse-json "Duration" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Duration.value"))

      (testing "interned data elements"
        (are [json key value] (identical? value (key (write-parse-json "Duration" json)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641")))

    (testing "XML"
      (testing "interned data elements"
        (are [xml key value] (identical? value (key (s2/conform :fhir.xml/Duration xml)))
          (sexp [nil {} [:unit {:value "151938"}]]) :unit #fhir/string-interned "151938"
          (sexp [nil {} [:system {:value "151921"}]]) :system #fhir/uri-interned "151921")))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "Duration" json))
        {}
        #fhir/Duration{}

        {:value 1M}
        #fhir/Duration{:value #fhir/decimal 1M})

      (testing "interned data elements"
        (are [cbor key value] (identical? value (key (write-parse-cbor "Duration" cbor)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641"))

      (testing "interning works"
        (are [x y] (identical? x y)
          (write-parse-cbor "Duration" {:unit "unit-183017"})
          (write-parse-cbor "Duration" {:unit "unit-183017"})

          (write-parse-cbor "Duration" {:system "system-151829"})
          (write-parse-cbor "Duration" {:system "system-151829"})))

      (testing "invalid"
        (given (write-parse-cbor "Duration" {:value "1"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `1`. Expected type is `decimal`."
          [:fhir/issues 0 :fhir.issues/expression] := "Duration.value"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Duration{}
        {}

        #fhir/Duration{:id "id-134908"}
        {:id "id-134908"}

        #fhir/Duration{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Duration{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Duration{:value #fhir/decimal 1M}
        {:value 1}

        #fhir/Duration{:comparator #fhir/code "code-153342"}
        {:comparator "code-153342"}

        #fhir/Duration{:unit #fhir/string "string-153351"}
        {:unit "string-153351"}

        #fhir/Duration{:system #fhir/uri "system-153337"}
        {:system "system-153337"}

        #fhir/Duration{:code #fhir/code "code-153427"}
        {:code "code-153427"}))))

(deftest ^:mem-size duration-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/duration))]
      (>= (Base/memSize (parse-cbor "Duration" source))
          (mem/total-size* (parse-cbor "Duration" source)
                           (parse-cbor "Duration" source)))))

  (testing "examples"
    (mem-size-test "Duration"
      {:value 11} 80)))

(deftest expression-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/expression)]
          (s2/valid? :fhir/Expression x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/expression)]
          (= (->> (write-json x)
                  (parse-json "Expression"))
             (->> (write-json x)
                  (parse-json "Expression")
                  (write-json)
                  (parse-json "Expression"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/expression)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Expression))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/expression)]
          (= (->> (write-cbor x)
                  (parse-cbor "Expression"))
             (->> (write-cbor x)
                  (parse-cbor "Expression")
                  (write-cbor)
                  (parse-cbor "Expression"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Expression" json))
        {}
        #fhir/Expression{}

        {:expression "expr-165511"}
        #fhir/Expression{:expression #fhir/string "expr-165511"})

      (testing "invalid"
        (given (write-parse-json "Expression" {:expression 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `string`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `string`."
          [:fhir/issues 0 :fhir.issues/expression] := "Expression.expression"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Expression{}
        {}

        #fhir/Expression{:id "id-134908"}
        {:id "id-134908"}

        #fhir/Expression{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Expression{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Expression{:expression #fhir/string "expr-165511"}
        {:expression "expr-165511"}))))

(deftest ^:mem-size expression-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/expression))]
      (>= (Base/memSize (parse-cbor "Expression" source))
          (mem/total-size* (parse-cbor "Expression" source)
                           (parse-cbor "Expression" source)))))

  (testing "examples"
    (mem-size-test "Expression"
      {:expression "expression-174835"} 104)))

(deftest extension-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 50
        (prop/for-all [x (fg/extension :value (fg/extension-value))]
          (s2/valid? :fhir/Extension x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/extension :value (fg/extension-value))]
          (= (->> (write-json x)
                  (parse-json "Extension"))
             (->> (write-json x)
                  (parse-json "Extension")
                  (write-json)
                  (parse-json "Extension"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/extension :value (fg/extension-value))]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Extension))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/extension :value (fg/extension-value))]
          (= (->> (write-cbor x)
                  (parse-cbor "Extension"))
             (->> (write-cbor x)
                  (parse-cbor "Extension")
                  (write-cbor)
                  (parse-cbor "Extension"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (testing "urls are interned"
        (let [e1 (write-parse-json "Extension" {:url (String. "foo") :valueString "bar"})
              e2 (write-parse-json "Extension" {:url (String. "foo") :valueString "bar"})]
          (is (identical? (:url e1) (:url e2)))))

      (are [json fhir] (= fhir (write-parse-json "Extension" json))
        {:url "foo" :valueString "bar"}
        #fhir/Extension{:url "foo" :value #fhir/string "bar"}

        {:url "foo" :valueCode "bar"}
        #fhir/Extension{:url "foo" :value #fhir/code "bar"}

        {:url "foo" :valueReference {:reference "bar"}}
        #fhir/Extension{:url "foo" :value #fhir/Reference{:reference #fhir/string "bar"}}

        {:url "foo" :valueCodeableConcept {:text "bar"}}
        #fhir/Extension{:url "foo" :value #fhir/CodeableConcept{:text #fhir/string "bar"}}

        {:url "foo" :extension [{:url "bar" :_valueDateTime {:extension [{:url "baz" :valueCode "qux"}]}}]}
        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code "qux"}]}}]}))

    (testing "XML"
      (testing "urls are interned"
        (let [e1 (s2/conform :fhir.xml/Extension (sexp [nil {:url (String. "foo")} [::f/valueString {:value "bar"}]]))
              e2 (s2/conform :fhir.xml/Extension (sexp [nil {:url (String. "foo")} [::f/valueString {:value "bar"}]]))]
          (is (identical? (:url e1) (:url e2)))))

      (are [xml fhir] (= fhir (s2/conform :fhir.xml/Extension xml))
        (sexp [nil {:url "foo"} [::f/valueString {:value "bar"}]])
        #fhir/Extension{:url "foo" :value #fhir/string "bar"}

        (sexp [nil {:url "foo"} [::f/valueCode {:value "bar"}]])
        #fhir/Extension{:url "foo" :value #fhir/code "bar"}

        (sexp [nil {:url "foo"} [::f/valueReference {} [::f/reference {:value "bar"}]]])
        #fhir/Extension{:url "foo" :value #fhir/Reference{:reference #fhir/string "bar"}}

        (sexp [nil {:url "foo"} [::f/valueCodeableConcept {} [::f/text {:value "bar"}]]])
        #fhir/Extension{:url "foo" :value #fhir/CodeableConcept{:text #fhir/string "bar"}}

        (sexp [nil {:url "foo"} [::f/extension {:url "bar"} [::f/valueDateTime {} [::f/extension {:url "baz"} [::f/valueCode {:value "qux"}]]]]])
        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code "qux"}]}}]}))

    (testing "CBOR"
      (testing "urls are interned"
        (let [e1 (write-parse-cbor "Extension" {:url (String. "foo") :valueString "bar"})
              e2 (write-parse-cbor "Extension" {:url (String. "foo") :valueString "bar"})]
          (is (identical? (:url e1) (:url e2)))))

      (are [cbor fhir] (= fhir (write-parse-cbor "Extension" cbor))
        {:url "foo" :valueString "bar"}
        #fhir/Extension{:url "foo" :value #fhir/string "bar"}

        {:url "foo" :valueCode "bar"}
        #fhir/Extension{:url "foo" :value #fhir/code "bar"}

        {:url "foo" :valueReference {:reference "bar"}}
        #fhir/Extension{:url "foo" :value #fhir/Reference{:reference #fhir/string "bar"}}

        {:url "foo" :valueCodeableConcept {:text "bar"}}
        #fhir/Extension{:url "foo" :value #fhir/CodeableConcept{:text #fhir/string "bar"}}

        {:url "foo" :extension [{:url "bar" :_valueDateTime {:extension [{:url "baz" :valueCode "qux"}]}}]}
        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code "qux"}]}}]})))

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

        #fhir/Extension{:value #fhir/code "code-135234"}
        {:valueCode "code-135234"}

        #fhir/Extension{:value #fhir/CodeableConcept{}}
        {:valueCodeableConcept {}}

        #fhir/Extension{:value #fhir/uuid "urn:uuid:935eb22d-cf35-4351-ae71-e517e49ebcbc"}
        {:valueUuid "urn:uuid:935eb22d-cf35-4351-ae71-e517e49ebcbc"}

        #fhir/Extension{:value #fhir/uuid{:id "id-172058" :value "urn:uuid:935eb22d-cf35-4351-ae71-e517e49ebcbc"}}
        {:valueUuid "urn:uuid:935eb22d-cf35-4351-ae71-e517e49ebcbc"
         :_valueUuid {:id "id-172058"}}

        #fhir/Extension{:value #fhir/CodeableConcept{:text #fhir/string "text-104840"}}
        {:valueCodeableConcept {:text "text-104840"}}

        #fhir/Extension{:value #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri "system-105127"}]}}
        {:valueCodeableConcept {:coding [{:system "system-105127"}]}}

        #fhir/Extension{:value #fhir/Annotation{:text #fhir/markdown "text-105422"}}
        {:valueAnnotation {:text "text-105422"}}

        #fhir/Extension{:value #fhir/Age{:value #fhir/decimal 1M}}
        {:valueAge {:value 1}}

        #fhir/Extension{:value #fhir/Count{:value #fhir/decimal 1M}}
        {:valueCount {:value 1}}

        #fhir/Extension{:value #fhir/Distance{:value #fhir/decimal 1M}}
        {:valueDistance {:value 1}}

        #fhir/Extension{:value #fhir/Duration{:value #fhir/decimal 1M}}
        {:valueDuration {:value 1}}

        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code "qux"}]}}]}
        {:url "foo" :extension [{:url "bar" :_valueDateTime {:extension [{:url "baz" :valueCode "qux"}]}}]}))

    (testing "XML"
      (are [fhir xml] (= xml (fhir-spec/unform-xml fhir))
        #fhir/Extension{}
        (sexp [])

        #fhir/Extension{:id "id-101320"}
        (sexp [nil {:id "id-101320"}])

        #fhir/Extension{:extension [#fhir/Extension{}]}
        (sexp [nil {} [::f/extension]])

        #fhir/Extension{:extension [#fhir/Extension{} #fhir/Extension{}]}
        (sexp [nil {} [::f/extension] [::f/extension]])

        #fhir/Extension{:url "url-185812"}
        (sexp [nil {:url "url-185812"}])

        #fhir/Extension{:value #fhir/Age{:value #fhir/decimal 1M}}
        (sexp [nil {} [::f/valueAge {} [::f/value {:value 1}]]])

        #fhir/Extension{:value #fhir/Count{:value #fhir/decimal 1M}}
        (sexp [nil {} [::f/valueCount {} [::f/value {:value 1}]]])

        #fhir/Extension{:value #fhir/Distance{:value #fhir/decimal 1M}}
        (sexp [nil {} [::f/valueDistance {} [::f/value {:value 1}]]])

        #fhir/Extension{:value #fhir/Duration{:value #fhir/decimal 1M}}
        (sexp [nil {} [::f/valueDuration {} [::f/value {:value 1}]]])

        #fhir/Extension{:value #fhir/Quantity{:value #fhir/decimal 1M}}
        (sexp [nil {} [::f/valueQuantity {} [::f/value {:value 1}]]])))

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

        #fhir/Extension{:value #fhir/code "code-135234"}
        {:valueCode "code-135234"}

        #fhir/Extension{:value #fhir/CodeableConcept{}}
        {:valueCodeableConcept {}}

        #fhir/Extension{:value #fhir/Address{}}
        {:valueAddress {}}

        #fhir/Extension{:value #fhir/Address{:city #fhir/string "foo"}}
        {:valueAddress {:city "foo"}}

        #fhir/Extension{:value #fhir/Age{:value #fhir/decimal 1M}}
        {:valueAge {:value 1M}}

        #fhir/Extension{:value #fhir/Count{:value #fhir/decimal 1M}}
        {:valueCount {:value 1M}}

        #fhir/Extension{:value #fhir/Distance{:value #fhir/decimal 1M}}
        {:valueDistance {:value 1M}}

        #fhir/Extension{:value #fhir/Duration{:value #fhir/decimal 1M}}
        {:valueDuration {:value 1M}}

        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code "qux"}]}}]}
        {:url "foo" :extension [{:url "bar" :_valueDateTime {:extension [{:url "baz" :valueCode "qux"}]}}]}))))

(deftest ^:mem-size extension-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/extension :value (fg/extension-value)))]
      (>= (Base/memSize (parse-cbor "Extension" source))
          (mem/total-size* (parse-cbor "Extension" source)
                           (parse-cbor "Extension" source)))))

  (testing "interning"
    (are [x y] (zero? (mem/total-size* x y))
      #fhir/Extension{:url "url-191107"}
      #fhir/Extension{:url "url-191107"}

      (write-parse-json "Extension" {:url "url-191107"})
      (write-parse-json "Extension" {:url "url-191107"}))))

(deftest human-name-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/human-name)]
          (s2/valid? :fhir/HumanName x)))))

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
        #fhir/HumanName{:use #fhir/code "usual"}

        {:given ["given-212441"]}
        #fhir/HumanName{:given [#fhir/string "given-212441"]}

        {:_given [{:extension [{:url "url-143610"}]}]}
        #fhir/HumanName{:given [#fhir/string{:extension [#fhir/Extension{:url "url-143610"}]}]}

        {:given ["given-143625"]
         :_given [{:extension [{:url "url-143619"}]}]}
        #fhir/HumanName{:given [#fhir/string{:extension [#fhir/Extension{:url "url-143619"}] :value "given-143625"}]}

        {:given ["given-212448" "given-212454"]}
        #fhir/HumanName{:given [#fhir/string "given-212448" #fhir/string "given-212454"]}

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
          [#fhir/string "given-143759"
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
        #fhir/HumanName{:use #fhir/code "usual"}

        {:given ["given-212441"]}
        #fhir/HumanName{:given [#fhir/string "given-212441"]})))

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

        #fhir/HumanName{:use #fhir/code "use-155449"}
        {:use "use-155449"}

        #fhir/HumanName{:text #fhir/string "text-141140"}
        {:text "text-141140"}

        #fhir/HumanName{:family #fhir/string "family-141158"}
        {:family "family-141158"}

        #fhir/HumanName{:given [#fhir/string "given-212441"]}
        {:given ["given-212441"]}

        #fhir/HumanName{:given [#fhir/string{:extension [#fhir/Extension{:url "url-143610"}]}]}
        {:_given [{:extension [{:url "url-143610"}]}]}

        #fhir/HumanName{:given [#fhir/string{:extension [#fhir/Extension{:url "url-143619"}] :value "given-143625"}]}
        {:given ["given-143625"]
         :_given [{:extension [{:url "url-143619"}]}]}

        #fhir/HumanName{:given [#fhir/string "given-212448" #fhir/string "given-212454"]}
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
          [#fhir/string "given-143759"
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

        #fhir/HumanName{:use #fhir/code "use-155449"}
        (sexp [nil {} [::f/use {:value "use-155449"}]])

        #fhir/HumanName{:text #fhir/string "text-141140"}
        (sexp [nil {} [::f/text {:value "text-141140"}]])

        #fhir/HumanName{:family #fhir/string "family-141158"}
        (sexp [nil {} [::f/family {:value "family-141158"}]])

        #fhir/HumanName{:given [#fhir/string "given-212441"]}
        (sexp [nil {} [::f/given {:value "given-212441"}]])

        #fhir/HumanName{:given [#fhir/string "given-212441" #fhir/string "given-170006"]}
        (sexp [nil {}
               [::f/given {:value "given-212441"}]
               [::f/given {:value "given-170006"}]])

        #fhir/HumanName{:period #fhir/Period{}}
        (sexp [nil {} [::f/period]])))))

(deftest ^:mem-size human-name-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/human-name))]
      (>= (Base/memSize (parse-cbor "HumanName" source))
          (mem/total-size* (parse-cbor "HumanName" source)
                           (parse-cbor "HumanName" source)))))

  (testing "examples"
    (mem-size-test "HumanName"
      {:given ["given-171612"]} 160)))

(deftest identifier-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (s2/valid? :fhir/Identifier x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (= (->> (write-json x)
                  (parse-json "Identifier"))
             (->> (write-json x)
                  (parse-json "Identifier")
                  (write-json)
                  (parse-json "Identifier"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Identifier))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (= (->> (write-cbor x)
                  (parse-cbor "Identifier"))
             (->> (write-cbor x)
                  (parse-cbor "Identifier")
                  (write-cbor)
                  (parse-cbor "Identifier"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Identifier" json))
        {}
        #fhir/Identifier{}

        {:use "usual"}
        #fhir/Identifier{:use #fhir/code "usual"}

        {:value "value-151311"}
        #fhir/Identifier{:value #fhir/string "value-151311"})

      (testing "invalid"
        (given (write-parse-json "Identifier" {:use 1})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on integer value 1. Expected type is `code`."
          [:fhir/issues 0 :fhir.issues/expression] := "Identifier.use"))

      (testing "interned data elements"
        (are [json key value] (identical? value (key (write-parse-json "Identifier" json)))
          {:system "152132"} :system #fhir/uri-interned "152132")))

    (testing "XML"
      (testing "interned data elements"
        (are [xml key value] (identical? value (key (s2/conform :fhir.xml/Identifier xml)))
          (sexp [nil {} [:system {:value "152057"}]]) :system #fhir/uri-interned "152057")))

    (testing "CBOR"
      (testing "interned data elements"
        (are [cbor key value] (identical? value (key (write-parse-json "Identifier" cbor)))
          {:system "152229"} :system #fhir/uri-interned "152229"))))

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

        #fhir/Identifier{:use #fhir/code "use-155449"}
        {:use "use-155449"}

        #fhir/Identifier{:type #fhir/CodeableConcept{}}
        {:type {}}

        #fhir/Identifier{:system #fhir/uri "system-160011"}
        {:system "system-160011"}

        #fhir/Identifier{:value #fhir/string "value-160034"}
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

        #fhir/Identifier{:use #fhir/code "use-155449"}
        (sexp [nil {} [::f/use {:value "use-155449"}]])

        #fhir/Identifier{:type #fhir/CodeableConcept{}}
        (sexp [nil {} [::f/type]])

        #fhir/Identifier{:system #fhir/uri "system-160011"}
        (sexp [nil {} [::f/system {:value "system-160011"}]])

        #fhir/Identifier{:value #fhir/string "value-160034"}
        (sexp [nil {} [::f/value {:value "value-160034"}]])

        #fhir/Identifier{:period #fhir/Period{}}
        (sexp [nil {} [::f/period]])

        #fhir/Identifier{:assigner #fhir/Reference{}}
        (sexp [nil {} [::f/assigner]])))))

(deftest ^:mem-size identifier-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/identifier))]
      (>= (Base/memSize (parse-cbor "Identifier" source))
          (mem/total-size* (parse-cbor "Identifier" source)
                           (parse-cbor "Identifier" source)))))

  (testing "examples"
    (mem-size-test "Identifier"
      {:use "use-192207"} 40
      {:system "system-113123"} 40
      {:value "string-151847"} 112)))

(deftest meta-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/meta)]
          (s2/valid? :fhir/Meta x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/meta)]
          (= (->> (write-json x)
                  (parse-json "Meta"))
             (->> (write-json x)
                  (parse-json "Meta")
                  (write-json)
                  (parse-json "Meta"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/meta)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Meta))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/meta)]
          (= (->> (write-cbor x)
                  (parse-cbor "Meta"))
             (->> (write-cbor x)
                  (parse-cbor "Meta")
                  (write-cbor)
                  (parse-cbor "Meta"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Meta" json))
        {}
        #fhir/Meta{}

        {:versionId "1"}
        #fhir/Meta{:versionId #fhir/id "1"}

        {:lastUpdated "1970-01-01T00:00:00Z"}
        (type/meta {:lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"}))))

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

        #fhir/Meta{:versionId #fhir/id "versionId-161812"}
        {:versionId "versionId-161812"}

        (type/meta {:lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})
        {:lastUpdated "1970-01-01T00:00:00Z"}

        #fhir/Meta{:source #fhir/uri "source-162704"}
        {:source "source-162704"}

        #fhir/Meta{:profile [#fhir/canonical "profile-uri-145024"]}
        {:profile ["profile-uri-145024"]}

        #fhir/Meta{:security [#fhir/Coding{}]}
        {:security [{}]}

        #fhir/Meta{:security [#fhir/Coding{} #fhir/Coding{}]}
        {:security [{} {}]}

        #fhir/Meta{:tag [#fhir/Coding{}]}
        {:tag [{}]}

        #fhir/Meta{:tag [#fhir/Coding{} #fhir/Coding{}]}
        {:tag [{} {}]}))))

(deftest ^:mem-size meta-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/meta))]
      (>= (Base/memSize (parse-cbor "Meta" source))
          (mem/total-size* (parse-cbor "Meta" source)
                           (parse-cbor "Meta" source)))))

  (testing "examples"
    (mem-size-test "Meta"
      {:versionId "id-172408"} 104)))

(deftest period-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/period)]
          (s2/valid? :fhir/Period x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/period)]
          (= (->> (write-json x)
                  (parse-json "Period"))
             (->> (write-json x)
                  (parse-json "Period")
                  (write-json)
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
             (->> (write-cbor x)
                  (parse-cbor "Period")
                  (write-cbor)
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
        #fhir/Period{:start #fhir/dateTime #system/date-time "2020"})

      (testing "invalid"
        (given (write-parse-json "Period" {:start "foo"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `date-time`. Text cannot be parsed to a DateTime"
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `date-time`. Text cannot be parsed to a DateTime"
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

        #fhir/Period{:start #fhir/dateTime #system/date-time "2020"}
        {:start "2020"}

        #fhir/Period{:end #fhir/dateTime #system/date-time "2020"}
        {:end "2020"}))))

(deftest ^:mem-size period-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/period))]
      (>= (Base/memSize (parse-cbor "Period" source))
          (mem/total-size* (parse-cbor "Period" source)
                           (parse-cbor "Period" source)))))

  (testing "examples"
    (mem-size-test "Period"
      {:start "2025"} 56
      {:start "2025" :end "2026"} 88)))

(deftest quantity-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/quantity)]
          (s2/valid? :fhir/Quantity x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/quantity)]
          (= (->> (write-json x)
                  (parse-json "Quantity"))
             (->> (write-json x)
                  (parse-json "Quantity")
                  (write-json)
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
             (->> (write-cbor x)
                  (parse-cbor "Quantity")
                  (write-cbor)
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
          [:fhir/issues 0 :fhir.issues/expression] := "Quantity.value"))

      (testing "interned data elements"
        (are [json key value] (identical? value (key (write-parse-json "Quantity" json)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641")))

    (testing "XML"
      (testing "interned data elements"
        (are [xml key value] (identical? value (key (s2/conform :fhir.xml/Quantity xml)))
          (sexp [nil {} [:unit {:value "151938"}]]) :unit #fhir/string-interned "151938"
          (sexp [nil {} [:system {:value "151921"}]]) :system #fhir/uri-interned "151921")))

    (testing "CBOR"
      (are [json fhir] (= fhir (write-parse-cbor "Quantity" json))
        {}
        #fhir/Quantity{}

        {:value 1M}
        #fhir/Quantity{:value #fhir/decimal 1M})

      (testing "interned data elements"
        (are [cbor key value] (identical? value (key (write-parse-cbor "Quantity" cbor)))
          {:unit "151658"} :unit #fhir/string-interned "151658"
          {:system "151641"} :system #fhir/uri-interned "151641"))

      (testing "interning works"
        (are [x y] (identical? x y)
          (write-parse-cbor "Quantity" {:unit "unit-183017"})
          (write-parse-cbor "Quantity" {:unit "unit-183017"})

          (write-parse-cbor "Quantity" {:system "system-151829"})
          (write-parse-cbor "Quantity" {:system "system-151829"})))

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

        #fhir/Quantity{:comparator #fhir/code "code-153342"}
        {:comparator "code-153342"}

        #fhir/Quantity{:unit #fhir/string "string-153351"}
        {:unit "string-153351"}

        #fhir/Quantity{:system #fhir/uri "system-153337"}
        {:system "system-153337"}

        #fhir/Quantity{:code #fhir/code "code-153427"}
        {:code "code-153427"}))))

(deftest ^:mem-size quantity-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/quantity))]
      (>= (Base/memSize (parse-cbor "Quantity" source))
          (mem/total-size* (parse-cbor "Quantity" source)
                           (parse-cbor "Quantity" source)))))

  (testing "examples"
    (mem-size-test "Quantity"
      {:value 11} 80)))

(deftest range-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/range)]
          (s2/valid? :fhir/Range x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/range)]
          (= (->> (write-json x)
                  (parse-json "Range"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/range)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Range))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/range)]
          (= (->> (write-cbor x)
                  (parse-cbor "Range"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Range" json))
        {}
        #fhir/Range{}

        {:id "id-151304"}
        #fhir/Range{:id "id-151304"}

        {:extension [{}]}
        #fhir/Range{:extension [#fhir/Extension{}]}

        {:low {:value 1M}}
        #fhir/Range{:low #fhir/Quantity{:value #fhir/decimal 1M}})

      (testing "invalid"
        (given (write-parse-json "Range" {:low "foo"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `Quantity`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `Quantity`."
          [:fhir/issues 0 :fhir.issues/expression] := "Range.low"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Range{}
        {}

        #fhir/Range{:id "id-134428"}
        {:id "id-134428"}

        #fhir/Range{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Range{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Range{:low #fhir/Quantity{:value #fhir/decimal 1M}}
        {:low {:value 1}}

        #fhir/Range{:high #fhir/Quantity{:value #fhir/decimal 1M}}
        {:high {:value 1}}))))

(deftest ^:mem-size range-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/range))]
      (>= (Base/memSize (parse-cbor "Range" source))
          (mem/total-size* (parse-cbor "Range" source)
                           (parse-cbor "Range" source)))))

  (testing "examples"
    (mem-size-test "Range"
      {:low {:value 11}} 104
      {:low {:value 11} :high {:value 12}} 184)))

(deftest ratio-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/ratio)]
          (s2/valid? :fhir/Ratio x)))))

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

(deftest ^:mem-size ratio-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/ratio))]
      (>= (Base/memSize (parse-cbor "Ratio" source))
          (mem/total-size* (parse-cbor "Ratio" source)
                           (parse-cbor "Ratio" source)))))

  (testing "examples"
    (mem-size-test "Ratio"
      {:numerator {:value 11}} 104
      {:numerator {:value 11} :denominator {:value 12}} 184)))

(deftest reference-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/reference)]
          (s2/valid? :fhir/Reference x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/reference)]
          (= (->> (write-json x)
                  (parse-json "Reference"))
             (->> (write-json x)
                  (parse-json "Reference")
                  (write-json)
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
             (->> (write-cbor x)
                  (parse-cbor "Reference")
                  (write-cbor)
                  (parse-cbor "Reference"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Reference" json))
        {}
        #fhir/Reference{}

        {:reference "Patient/1"}
        #fhir/Reference{:reference #fhir/string "Patient/1"})

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

        #fhir/Reference{:reference #fhir/string "Patient/1"}
        {:reference "Patient/1"}

        #fhir/Reference{:type #fhir/uri "type-161222"}
        {:type "type-161222"}

        #fhir/Reference{:identifier #fhir/Identifier{}}
        {:identifier {}}

        #fhir/Reference{:display #fhir/string "display-161314"}
        {:display "display-161314"}))))

(deftest ^:mem-size reference-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/reference))]
      (>= (Base/memSize (parse-cbor "Reference" source))
          (mem/total-size* (parse-cbor "Reference" source)
                           (parse-cbor "Reference" source)))))

  (testing "examples"
    (mem-size-test "Reference"
      {:reference "reference-171612"} 104)))

(deftest related-artifact-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/related-artifact)]
          (s2/valid? :fhir/RelatedArtifact x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/related-artifact)]
          (= (->> (write-json x)
                  (parse-json "RelatedArtifact"))
             (->> (write-json x)
                  (parse-json "RelatedArtifact")
                  (write-json)
                  (parse-json "RelatedArtifact"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/related-artifact)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/RelatedArtifact))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/related-artifact)]
          (= (->> (write-cbor x)
                  (parse-cbor "RelatedArtifact"))
             (->> (write-cbor x)
                  (parse-cbor "RelatedArtifact")
                  (write-cbor)
                  (parse-cbor "RelatedArtifact"))
             x))))))

(deftest ^:mem-size related-artifact-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/related-artifact))]
      (>= (Base/memSize (parse-cbor "RelatedArtifact" source))
          (mem/total-size* (parse-cbor "RelatedArtifact" source)
                           (parse-cbor "RelatedArtifact" source)))))

  (testing "examples"
    (mem-size-test "RelatedArtifact"
      {:label "label-175537"} 104)))

(deftest sampled-data-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/sampled-data)]
          (s2/valid? :fhir/SampledData x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/sampled-data)]
          (= (->> (write-json x)
                  (parse-json "SampledData"))
             (->> (write-json x)
                  (parse-json "SampledData")
                  (write-json)
                  (parse-json "SampledData"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/sampled-data)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/SampledData))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/sampled-data)]
          (= (->> (write-cbor x)
                  (parse-cbor "SampledData"))
             (->> (write-cbor x)
                  (parse-cbor "SampledData")
                  (write-cbor)
                  (parse-cbor "SampledData"))
             x))))))

(deftest ^:mem-size sampled-data-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/sampled-data))]
      (>= (Base/memSize (parse-cbor "SampledData" source))
          (mem/total-size* (parse-cbor "SampledData" source)
                           (parse-cbor "SampledData" source)))))

  (testing "examples"
    (mem-size-test "SampledData"
      {:data "data-161138"} 104)))

(deftest signature-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/signature)]
          (s2/valid? :fhir/Signature x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/signature :repeat (gen/return nil))]
          (= (->> (write-json x)
                  (parse-json "Signature"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/signature)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Signature))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/signature)]
          (= (->> (write-cbor x)
                  (parse-cbor "Signature"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Signature" json))
        {}
        #fhir/Signature{}

        {:id "id-151304"}
        #fhir/Signature{:id "id-151304"}

        {:extension [{}]}
        #fhir/Signature{:extension [#fhir/Extension{}]}

        {:type [{:code "code-114122"}]}
        #fhir/Signature{:type [#fhir/Coding{:code #fhir/code "code-114122"}]})

      (testing "invalid"
        (given (write-parse-json "Signature" {:type "foo"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `Coding`."
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `Coding`."
          [:fhir/issues 0 :fhir.issues/expression] := "Signature.type"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Signature{}
        {}

        #fhir/Signature{:id "id-134428"}
        {:id "id-134428"}

        #fhir/Signature{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Signature{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Signature{:type [#fhir/Coding{:code #fhir/code "code-114122"}]}
        {:type [{:code "code-114122"}]}))))

(deftest timing-repeat-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/timing-repeat)]
          (s2/valid? :fhir.Timing/repeat x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/timing-repeat)]
          (= (->> (write-json x)
                  (parse-json "Timing.repeat"))
             (->> (write-json x)
                  (parse-json "Timing.repeat")
                  (write-json)
                  (parse-json "Timing.repeat"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/timing-repeat)]
          (= (->> x
                  (s2/unform :fhir.xml.Timing/repeat)
                  (s2/conform :fhir.xml.Timing/repeat))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/timing-repeat)]
          (= (->> (write-cbor x)
                  (parse-cbor "Timing.repeat"))
             (->> (write-cbor x)
                  (parse-cbor "Timing.repeat")
                  (write-cbor)
                  (parse-cbor "Timing.repeat"))
             x))))))

(deftest ^:mem-size timing-repeat-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/timing-repeat))]
      (>= (Base/memSize (parse-cbor "Timing.repeat" source))
          (mem/total-size* (parse-cbor "Timing.repeat" source)
                           (parse-cbor "Timing.repeat" source)))))

  (testing "examples"
    (mem-size-test "Timing.repeat"
      {:count 1} 88)))

(deftest timing-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/timing)]
          (s2/valid? :fhir/Timing x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/timing :repeat (gen/return nil))]
          (= (->> (write-json x)
                  (parse-json "Timing"))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/timing)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Timing))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/timing)]
          (= (->> (write-cbor x)
                  (parse-cbor "Timing"))
             x)))))

  (testing "parsing"
    (testing "JSON"
      (are [json fhir] (= fhir (write-parse-json "Timing" json))
        {}
        #fhir/Timing{}

        {:id "id-151304"}
        #fhir/Timing{:id "id-151304"}

        {:extension [{}]}
        #fhir/Timing{:extension [#fhir/Extension{}]}

        {:event ["2025"]}
        #fhir/Timing{:event [#fhir/dateTime #system/date-time "2025"]})

      (testing "invalid"
        (given (write-parse-json "Timing" {:event "foo"})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid JSON representation of a resource. Error on value `foo`. Expected type is `date-time`. Text cannot be parsed to a DateTime"
          [:fhir/issues 0 :fhir.issues/code] := "invariant"
          [:fhir/issues 0 :fhir.issues/diagnostics] := "Error on value `foo`. Expected type is `date-time`. Text cannot be parsed to a DateTime"
          [:fhir/issues 0 :fhir.issues/expression] := "Timing.event"))))

  (testing "writing"
    (testing "JSON"
      (are [fhir json] (= json (write-read-json fhir))
        #fhir/Timing{}
        {}

        #fhir/Timing{:id "id-134428"}
        {:id "id-134428"}

        #fhir/Timing{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Timing{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Timing{:event [#fhir/dateTime #system/date-time "2025"]}
        {:event ["2025"]}))))

(deftest ^:mem-size timing-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/timing))]
      (>= (Base/memSize (parse-cbor "Timing" source))
          (mem/total-size* (parse-cbor "Timing" source)
                           (parse-cbor "Timing" source)))))

  (testing "examples"
    (mem-size-test "Timing"
      {:event ["2025"]} 120)))

(deftest bundle-entry-search-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry-search)]
          (s2/valid? :fhir.Bundle.entry/search x)))))

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
        #fhir.Bundle.entry/search{}

        {:id "id-134805"}
        #fhir.Bundle.entry/search{:id "id-134805"}

        {:mode "match"}
        #fhir.Bundle.entry/search{:mode #fhir/code "match"})

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
        #fhir.Bundle.entry/search{}

        {:id "id-134805"}
        #fhir.Bundle.entry/search{:id "id-134805"}

        {:mode "match"}
        #fhir.Bundle.entry/search{:mode #fhir/code "match"})

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
        #fhir.Bundle.entry/search{}
        {}

        #fhir.Bundle.entry/search{:id "id-115229"}
        {:id "id-115229"}

        #fhir.Bundle.entry/search{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir.Bundle.entry/search{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir.Bundle.entry/search{:mode #fhir/code "match"}
        {:mode "match"}

        #fhir.Bundle.entry/search{:score #fhir/decimal 1.1M}
        {:score 1.1}))

    (testing "CBOR"
      (are [fhir cbor] (= cbor (read-cbor (write-cbor fhir)))
        #fhir.Bundle.entry/search{}
        {}

        #fhir.Bundle.entry/search{:id "id-115229"}
        {:id "id-115229"}

        #fhir.Bundle.entry/search{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir.Bundle.entry/search{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir.Bundle.entry/search{:mode #fhir/code "match"}
        {:mode "match"}

        #fhir.Bundle.entry/search{:score #fhir/decimal 1.1M}
        {:score 1.1M}))))

(deftest ^:mem-size bundle-entry-search-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/bundle-entry-search))]
      (>= (Base/memSize (parse-cbor "Bundle.entry.search" source))
          (mem/total-size* (parse-cbor "Bundle.entry.search" source)
                           (parse-cbor "Bundle.entry.search" source)))))

  (testing "examples"
    (mem-size-test "Bundle.entry.search"
      {:score 11} 72)))

(deftest bundle-entry-resource-test
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
               :fullUrl #fhir/uri "uri-155734"})
             {:fullUrl "uri-155734"}))))

  (testing "references"
    (satisfies-prop 10
      (prop/for-all [x (fg/bundle-entry
                        :resource
                        (fg/observation
                         :subject
                         (fg/reference
                          :reference (gen/return #fhir/string "Patient/0"))))]
        (empty? (type/references x))))))

(deftest ^:mem-size bundle-entry-resource-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/bundle-entry))]
      (>= (Base/memSize (parse-cbor "Bundle.entry" source))
          (mem/total-size* (parse-cbor "Bundle.entry" source)
                           (parse-cbor "Bundle.entry" source)))))

  (testing "examples"
    (are [x size] (= (mem/total-size-exclude x :fhir/type :fhir.Bundle/entry :fullUrl)
                     (Base/memSize x) size)
      (write-parse-json "Bundle.entry" {:fullUrl "uri-155734"}) 120)))

;; ---- Resources -------------------------------------------------------------

(defn murmur3 [x]
  (let [hasher (.newHasher (Hashing/murmur3_32_fixed))]
    (Base/hashInto x hasher)
    (Integer/toHexString (.asInt (.hash hasher)))))

(deftest activity-definition-test
  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [activity-definition (fg/activity-definition)]
          (= (->> (write-json activity-definition)
                  (parse-json "ActivityDefinition"))
             (->> (write-json activity-definition)
                  (parse-json "ActivityDefinition")
                  (write-json)
                  (parse-json "ActivityDefinition"))
             activity-definition))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [activity-definition (fg/activity-definition)]
          (= (-> activity-definition
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             activity-definition))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [activity-definition (fg/activity-definition)]
          (= (->> (write-cbor activity-definition)
                  (parse-cbor "ActivityDefinition"))
             (->> (write-cbor activity-definition)
                  (parse-cbor "ActivityDefinition")
                  (write-cbor)
                  (parse-cbor "ActivityDefinition"))
             activity-definition))))))

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

  (testing "interned data elements"
    (are [json path value] (identical? value (get-in (write-parse-json "Bundle" json) path))
      {:link [{:relation "141802"}]} [:link 0 :relation] #fhir/string-interned "141802"
      {:entry [{:response {:status "200"}}]} [:entry 0 :response :status] #fhir/string-interned "200"))

  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json {:fhir/type :fhir/Bundle})
             {:resourceType "Bundle"}))

      (is (= (write-read-json
              {:fhir/type :fhir/Bundle
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :fullUrl #fhir/uri "url-104116"}]})
             {:resourceType "Bundle" :entry [{:fullUrl "url-104116"}]}))

      (is (= (write-read-json
              {:fhir/type :fhir/Bundle
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :resource {:fhir/type :fhir/Patient}}]})
             {:resourceType "Bundle" :entry [{:resource {:resourceType "Patient"}}]}))))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [bundle (fg/bundle)]
        (murmur3 bundle)))))

(deftest ^:mem-size bundle-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/bundle))]
      (>= (Base/memSize (parse-cbor "Bundle" source))
          (mem/total-size* (parse-cbor "Bundle" source)
                           (parse-cbor "Bundle" source)))))

  (testing "examples"
    (are [x size] (= (mem/total-size-exclude x :fhir/type :fhir/Bundle :total)
                     (Base/memSize x) size)
      (write-parse-json "Bundle" {:total 1}) 72)))

(deftest capability-statement-test
  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json
              {:fhir/type :fhir/CapabilityStatement
               :rest
               [{:searchParam
                 [{:name #fhir/string "name-151346"}]}]})
             {:resourceType "CapabilityStatement"
              :rest
              [{:searchParam
                [{:name "name-151346"}]}]})))))

(deftest patient-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/patient)]
          (s2/valid? :fhir/Patient x)))))

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
               :gender #fhir/code "female"
               :active #fhir/boolean true})
             {:resourceType "Patient" :active true :gender "female"}))))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [patient (fg/patient)]
        (murmur3 patient)))))

(deftest ^:mem-size patient-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/patient))]
      (>= (Base/memSize (parse-cbor "Patient" source))
          (mem/total-size* (parse-cbor "Patient" source)
                           (parse-cbor "Patient" source))))))

(deftest list-test
  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/List
       :entry
       [{:fhir/type :fhir.List/entry
         :item #fhir/Reference{:reference #fhir/string "Patient/0"}}
        {:fhir/type :fhir.List/entry
         :item #fhir/Reference{:reference #fhir/string "Patient/1"}}]}
      [["Patient" "0"]
       ["Patient" "1"]])))

(def ^:private observation-non-summary-properties
  #{:category :dataAbsentReason :interpretation :note :bodySite :method
    :specimen :device :referenceRange})

(deftest observation-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/observation)]
          (s2/valid? :fhir/Observation x)))))

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
       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}
      [["Patient" "0"]]))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [observation (fg/observation)]
        (murmur3 observation)))))

(deftest ^:mem-size observation-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/observation))]
      (>= (Base/memSize (parse-cbor "Observation" source))
          (mem/total-size* (parse-cbor "Observation" source)
                           (parse-cbor "Observation" source))))))

(deftest condition-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/condition)]
          (s2/valid? :fhir/Condition x)))))

  (testing "round-trip"
    (testing "JSON"
      (satisfies-prop 20
        (prop/for-all [condition (fg/condition)]
          (= (->> (write-json condition)
                  (parse-json "Condition"))
             condition))))

    (testing "XML"
      (satisfies-prop 20
        (prop/for-all [condition (fg/condition)]
          (= (-> condition
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             condition))))

    (testing "CBOR"
      (satisfies-prop 20
        (prop/for-all [condition (fg/condition)]
          (= (->> (write-cbor condition)
                  (parse-cbor "Condition"))
             condition)))))

  (testing "writing"
    (testing "JSON"
      (is (= (write-read-json
              {:fhir/type :fhir/Condition
               :onset #fhir/Age{:value #fhir/decimal 23M :code #fhir/code "a"}})
             {:resourceType "Condition"
              :onsetAge {:value 23 :code "a"}}))))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/Condition
       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}
      [["Patient" "0"]]))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [condition (fg/condition)]
        (murmur3 condition)))))

(deftest ^:mem-size condition-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/condition))]
      (>= (Base/memSize (parse-cbor "Condition" source))
          (mem/total-size* (parse-cbor "Condition" source)
                           (parse-cbor "Condition" source))))))

(deftest procedure-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/procedure)]
          (s2/valid? :fhir/Procedure x)))))

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
            {:value #fhir/Reference{:reference #fhir/string "Procedure/153904"}}]}
        #fhir/uri
         {:extension
          [#fhir/Extension
            {:value #fhir/Reference{:reference #fhir/string "Condition/153931"}}]}]
       :instantiatesUri
       [#fhir/uri
         {:extension
          [#fhir/Extension
            {:value #fhir/Reference{:reference #fhir/string "Patient/153540"}}]}
        #fhir/uri
         {:extension
          [#fhir/Extension
            {:value #fhir/Reference{:reference #fhir/string "Observation/153628"}}]}]}
      [["Procedure" "153904"]
       ["Condition" "153931"]
       ["Patient" "153540"]
       ["Observation" "153628"]]))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [procedure (fg/procedure)]
        (murmur3 procedure)))))

(deftest ^:mem-size procedure-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/procedure))]
      (>= (Base/memSize (parse-cbor "Procedure" source))
          (mem/total-size* (parse-cbor "Procedure" source)
                           (parse-cbor "Procedure" source))))))

(deftest allergy-intolerance-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/allergy-intolerance)]
          (s2/valid? :fhir/AllergyIntolerance x)))))

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
             allergy-intolerance)))))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [allergy-intolerance (fg/allergy-intolerance)]
        (murmur3 allergy-intolerance)))))

(deftest ^:mem-size allergy-intolerance-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/allergy-intolerance))]
      (>= (Base/memSize (parse-cbor "AllergyIntolerance" source))
          (mem/total-size* (parse-cbor "AllergyIntolerance" source)
                           (parse-cbor "AllergyIntolerance" source))))))

(def ^:private code-system-non-summary-properties
  #{:description :purpose :copyright :concept})

(deftest code-system-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/code-system)]
          (s2/valid? :fhir/CodeSystem x)))))

  (testing "summary parsing"
    (satisfies-prop 20
      (prop/for-all [code-system (fg/code-system)]
        (let [source (write-cbor code-system)
              code-system (parse-cbor "CodeSystem" source :summary)]
          (and
           (->> code-system :meta :tag (some fu/subsetted?))
           (not-any? code-system-non-summary-properties (keys code-system)))))))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [code-system (fg/code-system)]
        (murmur3 code-system)))))

(deftest ^:mem-size code-system-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/code-system))]
      (>= (Base/memSize (parse-cbor "CodeSystem" source))
          (mem/total-size* (parse-cbor "CodeSystem" source)
                           (parse-cbor "CodeSystem" source))))))

(def ^:private value-set-non-summary-properties
  #{:description :purpose :copyright :compose :expansion})

(deftest value-set-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/value-set)]
          (s2/valid? :fhir/ValueSet x)))))

  (testing "summary parsing"
    (satisfies-prop 20
      (prop/for-all [value-set (fg/value-set)]
        (let [source (write-cbor value-set)
              value-set (parse-cbor "ValueSet" source :summary)]
          (and
           (->> value-set :meta :tag (some fu/subsetted?))
           (not-any? value-set-non-summary-properties (keys value-set)))))))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [value-set (fg/value-set)]
        (murmur3 value-set)))))

(deftest ^:mem-size value-set-mem-size-test
  (satisfies-prop 50
    (prop/for-all [source (gen/fmap write-cbor (fg/value-set))]
      (>= (Base/memSize (parse-cbor "ValueSet" source))
          (mem/total-size* (parse-cbor "ValueSet" source)
                           (parse-cbor "ValueSet" source))))))

(deftest provenance-test
  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/Provenance
       :target
       [#fhir/Reference{:reference #fhir/string "Patient/204750"}
        #fhir/Reference{:reference #fhir/string "Observation/204754"}]}
      [["Patient" "204750"]
       ["Observation" "204754"]])))

(deftest task-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 20
        (prop/for-all [x (fg/task)]
          (s2/valid? :fhir/Task x)))))

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
           :value #fhir/Reference{:reference #fhir/string "bar"}}]})

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
                 :value #fhir/code "code-173329"}]})
             {:resourceType "Task"
              :input [{:valueCode "code-173329"}]}))))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [task (fg/task)]
        (murmur3 task)))))

(deftest library-test
  (testing "summary parsing"
    (satisfies-prop 20
      (prop/for-all [library (fg/library)]
        (let [source (write-cbor library)
              library (parse-cbor "Library" source :summary)]
          (and
           (->> library :meta :tag (some fu/subsetted?))
           (not-any? :data (:content library)))))))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [library (fg/library)]
        (murmur3 library)))))

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
              :policyRule {}}))))

  (testing "hash-into"
    (satisfies-prop 20
      (prop/for-all [consent (fg/consent)]
        (murmur3 consent)))))

(deftest ^:mem-size consent-mem-size-test
  (satisfies-prop 20
    (prop/for-all [consent (fg/consent)]
      (pos? (Base/memSize consent)))))
