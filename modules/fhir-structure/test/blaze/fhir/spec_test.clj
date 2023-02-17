(ns blaze.fhir.spec-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec-spec]
    [blaze.fhir.spec.generators :as fg]
    [blaze.fhir.spec.impl.util-spec]
    [blaze.fhir.spec.impl.xml-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.test-util :as tu :refer [satisfies-prop]]
    [clojure.alpha.spec :as s2]
    [clojure.data.xml.name :as xml-name]
    [clojure.data.xml.prxml :as prxml]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [java.nio.charset StandardCharsets]
    [java.time Instant]))


(xml-name/alias-uri 'f "http://hl7.org/fhir")
(xml-name/alias-uri 'xhtml "http://www.w3.org/1999/xhtml")


(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest parse-json-test
  (testing "fails on unexpected end-of-input"
    (given (fhir-spec/parse-json "{")
      ::anom/category := ::anom/incorrect
      ::anom/message :# "Unexpected end-of-input: expected close marker for Object(.|\\s)*"))

  (testing "fails on trailing token"
    (given (fhir-spec/parse-json "{}{")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Trailing token (of type START_OBJECT) found after value (bound as `java.lang.Object`): not allowed as per `DeserializationFeature.FAIL_ON_TRAILING_TOKENS`\n at [Source: (String)\"{}{\"; line: 1, column: 3]")))


(deftest parse-cbor-test
  (given (fhir-spec/parse-cbor (byte-array 0))
    ::anom/category := ::anom/incorrect
    ::anom/message :# "No content to map due to end-of-input(.|\\s)*"))


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
  (testing "valid"
    (are [s] (s/valid? :fhir/type s)
      :fhir/Patient))

  (testing "invalid"
    (are [s] (not (s/valid? :fhir/type s))
      "Patient"
      :Patient
      :fhir/patient)))


(deftest resource-id-test
  (are [s] (s/valid? :blaze.resource/id s)
    "."
    "-"
    "a"
    "A"
    "0"))


(deftest local-ref-spec-test
  (is (= ["Patient" "0"] (s/conform :blaze.fhir/local-ref "Patient/0")))

  (is (s/invalid? (s/conform :blaze.fhir/local-ref "Patient/0/1"))))


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


(deftest conform-json-test
  (testing "nil"
    (is (ba/anomaly? (fhir-spec/conform-json nil))))

  (testing "string"
    (is (ba/anomaly? (fhir-spec/conform-json "foo"))))

  (testing "invalid"
    (is (ba/anomaly? (fhir-spec/conform-json {:resourceType "Patient" :id 0}))))

  (testing "empty patient resource"
    (testing "gets type annotated"
      (is (= :fhir/Patient
             (fhir-spec/fhir-type
               (fhir-spec/conform-json
                 {:resourceType "Patient"})))))

    (testing "stays the same"
      (is (= {:fhir/type :fhir/Patient}
             (fhir-spec/conform-json {:resourceType "Patient"})))))

  (testing "deceasedBoolean on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased true}
           (fhir-spec/conform-json
             {:resourceType "Patient" :deceasedBoolean true}))))

  (testing "deceasedDateTime on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased #fhir/dateTime"2020"}
           (fhir-spec/conform-json
             {:resourceType "Patient" :deceasedDateTime "2020"}))))

  (testing "multipleBirthInteger on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :multipleBirth 2}
           (fhir-spec/conform-json
             {:resourceType "Patient" :multipleBirthInteger 2}))))

  (testing "Observation with code"
    (is (= {:fhir/type :fhir/Observation
            :code
            #fhir/CodeableConcept
                    {:coding
                     [#fhir/Coding
                             {:system #fhir/uri"http://loinc.org"
                              :code #fhir/code"39156-5"}]}}
           (fhir-spec/conform-json
             {:resourceType "Observation"
              :code {:coding [{:system "http://loinc.org" :code "39156-5"}]}}))))

  (testing "questionnaire resource with item groups"
    (is (= {:fhir/type :fhir/Questionnaire
            :item
            [{:fhir/type :fhir.Questionnaire/item
              :type #fhir/code"group"
              :item
              [{:fhir/type :fhir.Questionnaire/item
                :type #fhir/code"string"
                :text "foo"}]}]}
           (fhir-spec/conform-json
             {:resourceType "Questionnaire"
              :item
              [{:type "group"
                :item
                [{:type "string"
                  :text "foo"}]}]})))))


(defn- conform-xml [sexp]
  (fhir-spec/conform-xml (prxml/sexp-as-element sexp)))


(def ^:private sexp prxml/sexp-as-element)


(defn- sexp-value [value]
  (sexp [nil {:value value}]))


(deftest conform-xml-test
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


(defn remove-narrative [entry]
  (update entry :resource dissoc :text))


(defn- unform-json [resource]
  (String. ^bytes (fhir-spec/unform-json resource) StandardCharsets/UTF_8))


(deftest unform-json-test
  (testing "Patient with deceasedBoolean"
    (are [resource json] (= json (unform-json resource))
      {:fhir/type :fhir/Patient :deceased true}
      "{\"deceasedBoolean\":true,\"resourceType\":\"Patient\"}"))

  (testing "Patient with deceasedDateTime"
    (are [resource json] (= json (unform-json resource))
      {:fhir/type :fhir/Patient :deceased #fhir/dateTime"2020"}
      "{\"deceasedDateTime\":\"2020\",\"resourceType\":\"Patient\"}"))

  (testing "Patient with multipleBirthBoolean"
    (are [resource json] (= json (unform-json resource))
      {:fhir/type :fhir/Patient :multipleBirth false}
      "{\"multipleBirthBoolean\":false,\"resourceType\":\"Patient\"}"))

  (testing "Patient with multipleBirthInteger"
    (are [resource json] (= json (unform-json resource))
      {:fhir/type :fhir/Patient :multipleBirth (int 2)}
      "{\"multipleBirthInteger\":2,\"resourceType\":\"Patient\"}"))

  (testing "Bundle with Patient"
    (are [resource json] (= json (unform-json resource))
      {:fhir/type :fhir/Bundle
       :entry
       [{:fhir/type :fhir.Bundle/entry
         :resource {:fhir/type :fhir/Patient :id "0"}}]}
      "{\"entry\":[{\"resource\":{\"id\":\"0\",\"resourceType\":\"Patient\"}}],\"resourceType\":\"Bundle\"}"))

  (testing "Observation with code"
    (are [resource json] (= json (unform-json resource))
      {:fhir/type :fhir/Observation
       :code
       #fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                        {:system #fhir/uri"http://loinc.org"
                         :code #fhir/code"39156-5"}]}}
      "{\"code\":{\"coding\":[{\"system\":\"http://loinc.org\",\"code\":\"39156-5\"}]},\"resourceType\":\"Observation\"}"))

  (testing "Observation with valueQuantity"
    (are [resource json] (= json (unform-json resource))
      {:fhir/type :fhir/Observation
       :value
       #fhir/Quantity
               {:value 36.6M
                :unit #fhir/string"kg/m^2"
                :system #fhir/uri"http://unitsofmeasure.org"
                :code #fhir/code"kg/m2"}}
      "{\"valueQuantity\":{\"value\":36.6,\"unit\":\"kg/m^2\",\"system\":\"http://unitsofmeasure.org\",\"code\":\"kg/m2\"},\"resourceType\":\"Observation\"}")))


(deftest conform-cbor-test
  (testing "nil"
    (given (fhir-spec/conform-cbor nil)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid intermediate representation of a resource."
      :x := nil))

  (testing "empty map"
    (given (fhir-spec/conform-cbor {})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid intermediate representation of a resource."
      :x := {})))


(defn- conform-unform-cbor [resource]
  (-> (fhir-spec/unform-cbor resource)
      fhir-spec/parse-cbor
      fhir-spec/conform-cbor))


(deftest unform-cbor-test
  (testing "Patient with deceasedBoolean"
    (are [resource] (= resource (conform-unform-cbor resource))
      {:fhir/type :fhir/Patient :deceased true}))

  (testing "Patient with deceasedDateTime"
    (are [resource] (= resource (conform-unform-cbor resource))
      {:fhir/type :fhir/Patient :deceased #fhir/dateTime"2020"}))

  (testing "Patient with multipleBirthBoolean"
    (are [resource] (= resource (conform-unform-cbor resource))
      {:fhir/type :fhir/Patient :multipleBirth false}))

  (testing "Patient with multipleBirthInteger"
    (are [resource] (= resource (conform-unform-cbor resource))
      {:fhir/type :fhir/Patient :multipleBirth (int 2)}))

  (testing "Bundle with Patient"
    (are [resource] (= resource (conform-unform-cbor resource))
      {:fhir/type :fhir/Bundle
       :entry
       [{:fhir/type :fhir.Bundle/entry
         :resource {:fhir/type :fhir/Patient :id "0"}}]}))

  (testing "Observation with code"
    (are [resource] (= resource (conform-unform-cbor resource))
      {:fhir/type :fhir/Observation
       :code
       #fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                        {:system #fhir/uri"http://loinc.org"
                         :code #fhir/code"39156-5"}]}}))

  (testing "Observation with valueQuantity"
    (are [resource] (= resource (conform-unform-cbor resource))
      {:fhir/type :fhir/Observation
       :value
       #fhir/Quantity
               {:value 36.6M
                :unit #fhir/string"kg/m^2"
                :system #fhir/uri"http://unitsofmeasure.org"
                :code #fhir/code"kg/m2"}})))


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
                                         :display "divers"}}]
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


(deftest fhir-type-test
  (testing "Patient"
    (is (= :fhir/Patient
           (fhir-spec/fhir-type
             (fhir-spec/conform-json {:resourceType "Patient"}))))))


(deftest explain-data-json-test
  (testing "valid resources"
    (are [resource] (nil? (fhir-spec/explain-data-json resource))
      {:resourceType "Patient" :id "."}
      {:resourceType "Patient" :id "0"}))

  (testing "missing resource type"
    (given (fhir-spec/explain-data-json {})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "value"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Given resource does not contain a :resourceType key."))

  (testing "unknown resource type"
    (given (fhir-spec/explain-data-json {:resourceType "<unknown>"})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "value"
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Unknown resource type `<unknown>`."))

  (testing "invalid resource"
    (given (fhir-spec/explain-data-json
             {:resourceType "Patient" :name [{:use "" :text []}]})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      [:fhir/issues 0 :fhir.issues/expression] := "name[0].use"
      [:fhir/issues 1 :fhir.issues/severity] := "error"
      [:fhir/issues 1 :fhir.issues/code] := "invariant"
      [:fhir/issues 1 :fhir.issues/diagnostics] :=
      "Error on value `[]`. Expected type is `string`, regex `[ \\r\\n\\t\\S]+`."
      [:fhir/issues 1 :fhir.issues/expression] := "name[0].text"))

  (testing "invalid backbone-element"
    (given (fhir-spec/explain-data-json
             {:resourceType "Patient" :contact ""})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `Patient.contact`."
      [:fhir/issues 0 :fhir.issues/expression] := "contact[0]"))

  (testing "invalid non-primitive element"
    (given (fhir-spec/explain-data-json
             {:resourceType "Patient" :name ""})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `HumanName`."
      [:fhir/issues 0 :fhir.issues/expression] := "name[0]"))

  (testing "Include namespace part if more than fhir"
    (given (fhir-spec/explain-data-json
             {:resourceType "Patient" :contact [2]})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value `2`. Expected type is `Patient.contact`."
      [:fhir/issues 0 :fhir.issues/expression] := "contact[0]"))

  (testing "invalid non-primitive element and wrong type in list"
    (given (fhir-spec/explain-data-json
             {:resourceType "Patient" :name [1]})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value `1`. Expected type is `HumanName`."
      [:fhir/issues 0 :fhir.issues/expression] := "name[0]"))

  (testing "Bundle with invalid Patient gender"
    (given (fhir-spec/explain-data-json
             {:resourceType "Bundle"
              :entry
              [{:resource
                {:resourceType "Patient"
                 :gender 1}}]})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value `1`. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      [:fhir/issues 0 :fhir.issues/expression] := "entry[0].resource.gender"))

  (testing "Invalid Coding in Observation"
    (given (fhir-spec/explain-data-json
             {:resourceType "Observation"
              :code {:coding [{:system 1}]}})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value `1`. Expected type is `uri`, regex `\\S*`."
      [:fhir/issues 0 :fhir.issues/expression] := "code.coding[0].system")))


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
      "Given resource does not contain a :tag key."))

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
      "Error on value ``. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
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
      "Error on value ` `. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      ;; TODO: implement expression for XML
      (comment
        [:fhir/issues 0 :fhir.issues/expression] :=
        "entry[0].resource.gender"))))


(deftest primitive-test
  (are [spec] (fhir-spec/primitive? spec)
    :fhir/id))



;; ---- Primitive Types -------------------------------------------------------

(deftest fhir-boolean-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/boolean-value]
            (= (type/boolean value) (s2/conform :fhir.json/boolean value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/boolean-value (gen/return nil)])]
              (= (type/boolean {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.json/boolean
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               (some? value) (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/boolean x))
          "a"
          {})))

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
          "a")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/boolean-value]
            (= (type/boolean value) (s2/conform :fhir.cbor/boolean value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/boolean-value (gen/return nil)])]
              (= (type/boolean {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.cbor/boolean
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               (some? value) (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/boolean-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/boolean value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/boolean-value]
            (= (sexp-value (str value)) (s2/unform :fhir.xml/boolean (type/boolean value))))))

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
                                         :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/boolean-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/boolean value)))))))))


(deftest fhir-integer-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/integer-value]
            (= (type/integer value) (s2/conform :fhir.json/integer value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/integer-value (gen/return nil)])]
              (= (type/integer {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.json/integer
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/integer x))
          "a"
          {})))

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
          "a")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/integer-value]
            (= (type/integer value) (s2/conform :fhir.cbor/integer value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/integer-value (gen/return nil)])]
              (= (type/integer {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.cbor/integer
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/integer-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/integer value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/integer-value]
            (= (sexp-value (str value)) (s2/unform :fhir.xml/integer (type/integer value))))))

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
                                         :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/integer-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/integer value)))))))))


(deftest fhir-string-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/string-value]
            (= (type/string value) (s2/conform :fhir.json/string value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/string-value (gen/return nil)])]
              (= (type/string {:id id
                               :extension
                               [(type/extension {:url extension-url})]
                               :value value})
                 (s2/conform :fhir.json/string
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/string x))
          ""
          {})))

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
          "")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/string-value]
            (= (type/string value) (s2/conform :fhir.cbor/string value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/string-value (gen/return nil)])]
              (= (type/string {:id id
                               :extension
                               [(type/extension {:url extension-url})]
                               :value value})
                 (s2/conform :fhir.cbor/string
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/string-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/string value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/string-value]
            (= (sexp-value value) (s2/unform :fhir.xml/string (type/string value))))))

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
                                        :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/string-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/string value)))))))))


(deftest fhir-decimal-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/decimal-value]
            (= (type/decimal value) (s2/conform :fhir.json/decimal value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/decimal-value
                                              fg/integer-value
                                              (gen/return nil)])]
              (= (type/decimal {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.json/decimal
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/decimal x))
          "a"
          {})))

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
          "a")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/decimal-value]
            (= (type/decimal value) (s2/conform :fhir.cbor/decimal value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/decimal-value (gen/return nil)])]
              (= (type/decimal {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.cbor/decimal
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/decimal-value]
          (= value (bigdec (fhir-spec/parse-json (fhir-spec/unform-json (type/decimal value))))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/decimal-value]
            (= (sexp-value (str value)) (s2/unform :fhir.xml/decimal (type/decimal value))))))

      (testing "with extension"
        (satisfies-prop 100
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
                                         :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/decimal-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/decimal value)))))))))


(deftest fhir-uri-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/uri-value]
            (= (type/uri value) (s2/conform :fhir.json/uri value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/uri-value (gen/return nil)])]
              (= (type/uri {:id id
                            :extension
                            [(type/extension {:url extension-url})]
                            :value value})
                 (s2/conform :fhir.json/uri
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/uri x))
          " "
          {})))

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
          " ")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/uri-value]
            (= (type/uri value) (s2/conform :fhir.cbor/uri value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/uri-value (gen/return nil)])]
              (= (type/uri {:id id
                            :extension
                            [(type/extension {:url extension-url})]
                            :value value})
                 (s2/conform :fhir.cbor/uri
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/uri-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/uri value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/uri-value]
            (= (sexp-value value) (s2/unform :fhir.xml/uri (type/uri value))))))

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
                                     :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/uri-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/uri value)))))))))


(deftest fhir-url-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/url-value]
            (= (type/url value) (s2/conform :fhir.json/url value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/url-value (gen/return nil)])]
              (= (type/url {:id id
                            :extension
                            [(type/extension {:url extension-url})]
                            :value value})
                 (s2/conform :fhir.json/url
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/url x))
          " "
          {})))

    (testing "XML"
      (testing "valid"
        (satisfies-prop 100
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
          " ")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/url-value]
            (= (type/url value) (s2/conform :fhir.cbor/url value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/url-value (gen/return nil)])]
              (= (type/url {:id id
                            :extension
                            [(type/extension {:url extension-url})]
                            :value value})
                 (s2/conform :fhir.cbor/url
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/url-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/url value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/url-value]
            (= (sexp-value value) (s2/unform :fhir.xml/url (type/url value))))))

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
                                     :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/url-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/url value)))))))))


(deftest fhir-canonical-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/canonical-value]
            (= (type/canonical value) (s2/conform :fhir.json/canonical value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/canonical-value (gen/return nil)])]
              (= (type/canonical {:id id
                                  :extension
                                  [(type/extension {:url extension-url})]
                                  :value value})
                 (s2/conform :fhir.json/canonical
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/canonical x))
          " "
          {})))

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
          " ")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/canonical-value]
            (= (type/canonical value) (s2/conform :fhir.cbor/canonical value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/canonical-value (gen/return nil)])]
              (= (type/canonical {:id id
                                  :extension
                                  [(type/extension {:url extension-url})]
                                  :value value})
                 (s2/conform :fhir.cbor/canonical
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/canonical-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/canonical value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/canonical-value]
            (= (sexp-value value) (s2/unform :fhir.xml/canonical (type/canonical value))))))

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
                                           :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/canonical-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/canonical value)))))))))


(deftest fhir-base64Binary-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/base64Binary-value]
            (= (type/base64Binary value) (s2/conform :fhir.json/base64Binary value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/base64Binary-value (gen/return nil)])]
              (= (type/base64Binary {:id id
                                     :extension
                                     [(type/extension {:url extension-url})]
                                     :value value})
                 (s2/conform :fhir.json/base64Binary
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/base64Binary x))
          ""
          {})))

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
          "")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/base64Binary-value]
            (= (type/base64Binary value) (s2/conform :fhir.cbor/base64Binary value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/base64Binary-value (gen/return nil)])]
              (= (type/base64Binary {:id id
                                     :extension
                                     [(type/extension {:url extension-url})]
                                     :value value})
                 (s2/conform :fhir.cbor/base64Binary
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/base64Binary-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/base64Binary value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/base64Binary-value]
            (= (sexp-value value) (s2/unform :fhir.xml/base64Binary (type/base64Binary value))))))

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
                                              :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/base64Binary-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/base64Binary value)))))))))


(deftest fhir-instant-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/instant-value]
            (= (type/instant value) (s2/conform :fhir.json/instant value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/instant-value (gen/return nil)])]
              (= (type/instant {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.json/instant
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [s] (s2/invalid? (s2/conform :fhir.json/instant s))
          "2019-13"
          "2019-02-29"
          {})))

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
            "2019-02-29"))))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/instant-value]
            (= (type/instant value) (s2/conform :fhir.cbor/instant value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/instant-value (gen/return nil)])]
              (= (type/instant {:id id
                                :extension
                                [(type/extension {:url extension-url})]
                                :value value})
                 (s2/conform :fhir.cbor/instant
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/instant-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/instant value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/instant-value]
            (= (sexp-value value) (s2/unform :fhir.xml/instant (type/instant value))))))

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
                                         :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/instant-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/instant value)))))))))


(deftest fhir-date-test
  (testing "valid"
    (are [x] (s2/valid? :fhir/date x)
      #fhir/date{:extension [#fhir/Extension{:url "foo" :value #fhir/code"bar"}]}))

  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/date-value]
            (= (type/date value) (s2/conform :fhir.json/date value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/date-value (gen/return nil)])]
              (= (type/date {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.json/date
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [s] (s2/invalid? (s2/conform :fhir.json/date s))
          "2019-13"
          "2019-02-29"
          {})))

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
          "2019-02-29")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/date-value]
            (= (type/date value) (s2/conform :fhir.cbor/date value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/date-value (gen/return nil)])]
              (= (type/date {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.cbor/date
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/date-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/date value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/date-value]
            (= (sexp-value value) (s2/unform :fhir.xml/date (type/date value))))))

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
                                      :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/date-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/date value)))))))))


(deftest fhir-dateTime-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/dateTime-value]
            (= (type/dateTime value) (s2/conform :fhir.json/dateTime value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/dateTime-value (gen/return nil)])]
              (= (type/dateTime {:id id
                                 :extension
                                 [(type/extension {:url extension-url})]
                                 :value value})
                 (s2/conform :fhir.json/dateTime
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [s] (s2/invalid? (s2/conform :fhir.json/dateTime s))
          "2019-13"
          "2019-02-29"
          {})))

    (testing "XML"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/dateTime-value]
            (= (type/dateTime value) (s2/conform :fhir.xml/dateTime (sexp-value value)))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/dateTime-value (gen/return nil)])]
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
            "2019-02-29"))))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/dateTime-value]
            (= (type/dateTime value) (s2/conform :fhir.cbor/dateTime value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/dateTime-value (gen/return nil)])]
              (= (type/dateTime {:id id
                                 :extension
                                 [(type/extension {:url extension-url})]
                                 :value value})
                 (s2/conform :fhir.cbor/dateTime
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/dateTime-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/dateTime value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/dateTime-value]
            (= (sexp-value value) (s2/unform :fhir.xml/dateTime (type/dateTime value))))))

      (testing "with extension"
        (satisfies-prop 100
          (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                         extension-url fg/uri-value
                         value (gen/one-of [fg/dateTime-value (gen/return nil)])]
            (= (sexp
                 [nil (cond-> {} id (assoc :id id) value (assoc :value value))
                  [::f/extension {:url extension-url}]])
               (s2/unform :fhir.xml/dateTime
                          (type/dateTime {:id id
                                          :extension
                                          [(type/extension {:url extension-url})]
                                          :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/dateTime-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/dateTime value)))))))))


(deftest fhir-time-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/time-value]
            (= (type/time value) (s2/conform :fhir.json/time value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/time-value (gen/return nil)])]
              (= (type/time {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.json/time
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [s] (s2/invalid? (s2/conform :fhir.json/time s))
          "24:00"
          "24:00:00"
          {})))

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
            "24:00:00"))))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 1000
          (prop/for-all [value fg/time-value]
            (= (type/time value) (s2/conform :fhir.cbor/time value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/time-value (gen/return nil)])]
              (= (type/time {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.cbor/time
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/time-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/time value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/time-value]
            (= (sexp-value value) (s2/unform :fhir.xml/time (type/time value))))))

      (testing "with extension"
        (satisfies-prop 1000
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
                                      :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/time-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/time value)))))))))


(deftest fhir-code-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/code-value]
            (= (type/code value) (s2/conform :fhir.json/code value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/code-value (gen/return nil)])]
              (= (type/code {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.json/code
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/code x))
          ""
          {})))

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
          "")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/code-value]
            (= (type/code value) (s2/conform :fhir.cbor/code value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/code-value (gen/return nil)])]
              (= (type/code {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.cbor/code
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/code-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/code value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/code-value]
            (= (sexp-value value) (s2/unform :fhir.xml/code (type/code value))))))

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
                                      :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/code-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/code value)))))))))


(deftest fhir-oid-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/oid-value]
            (= (type/oid value) (s2/conform :fhir.json/oid value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/oid-value (gen/return nil)])]
              (= (type/oid {:id id
                            :extension
                            [(type/extension {:url extension-url})]
                            :value value})
                 (s2/conform :fhir.json/oid
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/oid x))
          ""
          {})))

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
          "")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/oid-value]
            (= (type/oid value) (s2/conform :fhir.cbor/oid value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/oid-value (gen/return nil)])]
              (= (type/oid {:id id
                            :extension
                            [(type/extension {:url extension-url})]
                            :value value})
                 (s2/conform :fhir.cbor/oid
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/oid-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/oid value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/oid-value]
            (= (sexp-value value) (s2/unform :fhir.xml/oid (type/oid value))))))

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
                                     :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/oid-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/oid value)))))))))


(deftest fhir-id-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/id-value]
            (= (type/id value) (s2/conform :fhir.json/id value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/id-value (gen/return nil)])]
              (= (type/id {:id id
                           :extension
                           [(type/extension {:url extension-url})]
                           :value value})
                 (s2/conform :fhir.json/id
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/id x))
          ""
          {})))

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
          "")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/id-value]
            (= (type/id value) (s2/conform :fhir.cbor/id value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/id-value (gen/return nil)])]
              (= (type/id {:id id
                           :extension
                           [(type/extension {:url extension-url})]
                           :value value})
                 (s2/conform :fhir.cbor/id
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/id-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/id value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/id-value]
            (= (sexp-value value) (s2/unform :fhir.xml/id (type/id value))))))

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
                                    :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/id-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/id value)))))))))


(deftest fhir-markdown-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/markdown-value]
            (= (type/markdown value) (s2/conform :fhir.json/markdown value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/markdown-value (gen/return nil)])]
              (= (type/markdown {:id id
                                 :extension
                                 [(type/extension {:url extension-url})]
                                 :value value})
                 (s2/conform :fhir.json/markdown
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/markdown x))
          ""
          {})))

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
          "")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/markdown-value]
            (= (type/markdown value) (s2/conform :fhir.cbor/markdown value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/markdown-value (gen/return nil)])]
              (= (type/markdown {:id id
                                 :extension
                                 [(type/extension {:url extension-url})]
                                 :value value})
                 (s2/conform :fhir.cbor/markdown
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/markdown-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/markdown value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/markdown-value]
            (= (sexp-value value) (s2/unform :fhir.xml/markdown (type/markdown value))))))

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
                                          :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/markdown-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/markdown value)))))))))


(deftest fhir-unsignedInt-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/unsignedInt-value]
            (= (type/unsignedInt value) (s2/conform :fhir.json/unsignedInt value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/unsignedInt-value (gen/return nil)])]
              (= (type/unsignedInt {:id id
                                    :extension
                                    [(type/extension {:url extension-url})]
                                    :value value})
                 (s2/conform :fhir.json/unsignedInt
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/unsignedInt x))
          ""
          {})))

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
          "")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/unsignedInt-value]
            (= (type/unsignedInt value) (s2/conform :fhir.cbor/unsignedInt value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/unsignedInt-value (gen/return nil)])]
              (= (type/unsignedInt {:id id
                                    :extension
                                    [(type/extension {:url extension-url})]
                                    :value value})
                 (s2/conform :fhir.cbor/unsignedInt
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/unsignedInt-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/unsignedInt value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/unsignedInt-value]
            (= (sexp-value value) (s2/unform :fhir.xml/unsignedInt (type/unsignedInt value))))))

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
                                             :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/unsignedInt-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/unsignedInt value)))))))))


(deftest fhir-positiveInt-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/positiveInt-value]
            (= (type/positiveInt value) (s2/conform :fhir.json/positiveInt value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/positiveInt-value (gen/return nil)])]
              (= (type/positiveInt {:id id
                                    :extension
                                    [(type/extension {:url extension-url})]
                                    :value value})
                 (s2/conform :fhir.json/positiveInt
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/positiveInt x))
          ""
          {})))

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
          "")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/positiveInt-value]
            (= (type/positiveInt value) (s2/conform :fhir.cbor/positiveInt value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/positiveInt-value (gen/return nil)])]
              (= (type/positiveInt {:id id
                                    :extension
                                    [(type/extension {:url extension-url})]
                                    :value value})
                 (s2/conform :fhir.cbor/positiveInt
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/positiveInt-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/positiveInt value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/positiveInt-value]
            (= (sexp-value value) (s2/unform :fhir.xml/positiveInt (type/positiveInt value))))))

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
                                             :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/positiveInt-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/positiveInt value)))))))))


(deftest fhir-uuid-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/uuid-value]
            (= (type/uuid value) (s2/conform :fhir.json/uuid value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/uuid-value (gen/return nil)])]
              (= (type/uuid {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.json/uuid
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))

      (testing "invalid"
        (are [x] (s2/invalid? (s2/conform :fhir.json/uuid x))
          ""
          {})))

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
          "")))

    (testing "CBOR"
      (testing "valid"
        (satisfies-prop 100
          (prop/for-all [value fg/uuid-value]
            (= (type/uuid value) (s2/conform :fhir.cbor/uuid value))))

        (testing "with extension"
          (satisfies-prop 100
            (prop/for-all [id (gen/one-of [fg/id-value (gen/return nil)])
                           extension-url fg/uri-value
                           value (gen/one-of [fg/uuid-value (gen/return nil)])]
              (= (type/uuid {:id id
                             :extension
                             [(type/extension {:url extension-url})]
                             :value value})
                 (s2/conform :fhir.cbor/uuid
                             (cond->
                               {:extension [{:url extension-url}]}
                               id (assoc :id id)
                               value (assoc :value value))))))))))

  (testing "unforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [value fg/uuid-value]
          (= value (fhir-spec/parse-json (fhir-spec/unform-json (type/uuid value)))))))

    (testing "XML"
      (testing "value only"
        (satisfies-prop 100
          (prop/for-all [value fg/uuid-value]
            (= (sexp-value value) (s2/unform :fhir.xml/uuid (type/uuid value))))))

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
                                      :value value})))))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [value fg/uuid-value]
          (= value (fhir-spec/parse-cbor (fhir-spec/unform-cbor (type/uuid value)))))))))


(def xhtml-element
  (sexp
    [::xhtml/div {:xmlns "http://www.w3.org/1999/xhtml"}
     [::xhtml/p "FHIR is cool."]]))


(deftest fhir-xhtml-test
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/xhtml s)
      #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"></div>"))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/xhtml s)
      "<div xmlns=\"http://www.w3.org/1999/xhtml\"></div>"))

  (testing "conforming"
    (testing "JSON"
      (is (= #fhir/xhtml"foo" (s2/conform :fhir.json/xhtml "foo"))))
    (testing "XML"
      (is (= #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"
             (s2/conform :fhir.xml/xhtml xhtml-element)))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        #fhir/xhtml"foo"
        "\"foo\""))
    (testing "XML"
      (is (= xhtml-element
             (s2/unform :fhir.xml/xhtml #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"))))))


;; ---- Complex Types ---------------------------------------------------------

(deftest attachment-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 1000
        (prop/for-all [x (fg/attachment)]
          (s2/valid? :fhir/Attachment x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Attachment x))
        #fhir/Attachment{:contentType "foo"})))

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 1000
        (prop/for-all [x (fg/attachment)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/Attachment))
             x))))

    (testing "XML"
      (satisfies-prop 1000
        (prop/for-all [x (fg/attachment)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Attachment))
             x))))

    (testing "CBOR"
      (satisfies-prop 1000
        (prop/for-all [x (fg/attachment)]
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/Attachment))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Attachment json))
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

        {:contentType 1}
        ::s2/invalid

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
        (is (= (s2/conform :fhir.json/Attachment {::unknown "unknown"})
               #fhir/Attachment{})))

      (testing "invalid underscore properties are ignored"
        (is (= (s2/conform :fhir.json/Attachment {:_contentType "foo"})
               #fhir/Attachment{}))))

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
        #fhir/Attachment{:size #fhir/unsignedInt{:extension [#fhir/Extension{:url "url-130946"}]}}
        )))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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
        (sexp [nil {} [::f/creation {:value "2021"}]])))))


(deftest extension-url-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (are [json fhir] (= fhir (s2/conform :fhir.json.Extension/url json))
          "" ""
          "url-103640" "url-103640"))

      (testing "invalid"
        (are [json] (not (s2/valid? :fhir.json.Extension/url json))
          " "
          1)))))


(deftest extension-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 1000
        (prop/for-all [x (fg/extension)]
          (s2/valid? :fhir/Extension x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Extension x))
        #fhir/Extension{:url 1})))

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 1000
        (prop/for-all [x (fg/extension)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/Extension))
             x))))

    (testing "XML"
      (satisfies-prop 1000
        (prop/for-all [x (fg/extension)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Extension))
             x))))

    (testing "CBOR"
      (satisfies-prop 1000
        (prop/for-all [x (fg/extension)]
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/Extension))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Extension json))
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
      (are [json fhir] (= fhir (s2/conform :fhir.cbor/Extension json))
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

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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

        #fhir/Extension{:value #fhir/CodeableConcept{:text #fhir/string"text-104840"}}
        {:valueCodeableConcept {:text "text-104840"}}

        #fhir/Extension{:value #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri"system-105127"}]}}
        {:valueCodeableConcept {:coding [{:system "system-105127"}]}}

        #fhir/Extension{:value {:fhir/type :fhir/Annotation :text "text-105422"}}
        {:valueAnnotation {:text "text-105422"}}

        #fhir/Extension{:url "foo" :extension [#fhir/Extension{:url "bar" :value #fhir/dateTime{:extension [#fhir/Extension{:url "baz" :value #fhir/code"qux"}]}}]}
        {:url "foo" :extension [{:url "bar" :_valueDateTime {:extension [{:url "baz" :valueCode "qux"}]}}]}))

    (testing "CBOR"
      (are [fhir cbor] (= cbor (fhir-spec/parse-cbor (fhir-spec/unform-cbor fhir)))
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
      (satisfies-prop 1000
        (prop/for-all [x (fg/coding)]
          (s2/valid? :fhir/Coding x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Coding x))
        #fhir/Coding{:system "foo"})))

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 1000
        (prop/for-all [x (fg/coding)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/Coding))
             x))))

    (testing "XML"
      (satisfies-prop 1000
        (prop/for-all [x (fg/coding)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Coding))
             x))))

    (testing "CBOR"
      (satisfies-prop 1000
        (prop/for-all [x (fg/coding)]
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/Coding))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Coding json))
        {:system "foo" :code "bar"}
        #fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"})))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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

        #fhir/Coding{:version "version-185951"}
        {:version "version-185951"}

        #fhir/Coding{:code #fhir/code"code-190226"}
        {:code "code-190226"}

        #fhir/Coding{:display "display-190327"}
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

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/codeable-concept)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/CodeableConcept))
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
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/CodeableConcept))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/CodeableConcept json))
        {}
        #fhir/CodeableConcept{}
        {:coding [{}]}
        #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
        {:text "text-223528"}
        #fhir/CodeableConcept{:text #fhir/string"text-223528"})))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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


(deftest quantity-unit-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (are [json fhir] (= fhir (s2/conform :fhir.json.Quantity/unit json))
          " " #fhir/string" "
          "unit-103640" #fhir/string"unit-103640"))

      (testing "invalid"
        (are [json] (not (s2/valid? :fhir.json.Quantity/unit json))
          ""
          1)))))


(deftest quantity-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 1000
        (prop/for-all [x (fg/quantity)]
          (s2/valid? :fhir/Quantity x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Quantity x))
        #fhir/Quantity{:value "1"})))

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 1000
        (prop/for-all [x (fg/quantity)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/Quantity))
             x))))

    (testing "XML"
      (satisfies-prop 1000
        (prop/for-all [x (fg/quantity)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Quantity))
             x))))

    (testing "CBOR"
      (satisfies-prop 1000
        (prop/for-all [x (fg/quantity)]
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/Quantity))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Quantity json))
        {}
        #fhir/Quantity{}

        {:value 1M}
        #fhir/Quantity{:value 1M}

        {:value "1"}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        #fhir/Quantity{}
        {}

        #fhir/Quantity{:id "id-134908"}
        {:id "id-134908"}

        #fhir/Quantity{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Quantity{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Quantity{:value 1M}
        {:value 1}

        #fhir/Quantity{:comparator #fhir/code"code-153342"}
        {:comparator "code-153342"}

        #fhir/Quantity{:unit #fhir/string"string-153351"}
        {:unit "string-153351"}

        #fhir/Quantity{:system #fhir/uri"system-153337"}
        {:system "system-153337"}

        #fhir/Quantity{:code #fhir/code"code-153427"}
        {:code "code-153427"}))))


(deftest period-test
  (testing "FHIR spec"
    (testing "valid"
      (satisfies-prop 1000
        (prop/for-all [x (fg/period)]
          (s2/valid? :fhir/Period x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Period x))
        #fhir/Period{:start "2020"})))

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 1000
        (prop/for-all [x (fg/period)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/Period))
             x))))

    (testing "XML"
      (satisfies-prop 1000
        (prop/for-all [x (fg/period)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Period))
             x))))

    (testing "CBOR"
      (satisfies-prop 1000
        (prop/for-all [x (fg/period)]
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/Period))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Period json))
        {}
        #fhir/Period{}

        {:id "id-151304"}
        #fhir/Period{:id "id-151304"}

        {:extension [{}]}
        #fhir/Period{:extension [#fhir/Extension{}]}

        {:start "2020"}
        #fhir/Period{:start #fhir/dateTime"2020"}

        {:start "foo"}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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
      (satisfies-prop 100
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (s2/valid? :fhir/Identifier x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Identifier x))
        #fhir/Identifier{:use "usual"})))

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/identifier :assigner (fg/often-nil (fg/reference)))]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/Identifier))
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
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/Identifier))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Identifier json))
        {}
        #fhir/Identifier{}

        {:use "usual"}
        #fhir/Identifier{:use #fhir/code"usual"}

        {:value "value-151311"}
        #fhir/Identifier{:value #fhir/string"value-151311"}

        {:use 1}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/human-name)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/HumanName))
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
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/HumanName))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/HumanName json))
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
                  #fhir/string{:extension [#fhir/Extension{:url "url-143806"}]}]}

        {:use 1}
        ::s2/invalid))

    (testing "CBOR"
      (are [cbor fhir] (= fhir (s2/conform :fhir.cbor/HumanName cbor))
        {}
        #fhir/HumanName{}

        {:use "usual"}
        #fhir/HumanName{:use #fhir/code"usual"}

        {:given ["given-212441"]}
        #fhir/HumanName{:given ["given-212441"]})))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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
      (satisfies-prop 100
        (prop/for-all [x (fg/address)]
          (s2/valid? :fhir/Address x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Address x))
        #fhir/Address{:use "usual"})))

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/address)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/Address))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/address)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Address))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/address)]
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/Address))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Address json))
        {}
        #fhir/Address{}

        {:use "usual"}
        #fhir/Address{:use #fhir/code"usual"}

        {:use 1}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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
      (satisfies-prop 1000
        (prop/for-all [x (fg/reference)]
          (s2/valid? :fhir/Reference x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Reference x))
        #fhir/Reference{:reference 1})))

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 1000
        (prop/for-all [x (fg/reference)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/Reference))
             x))))

    (testing "XML"
      (satisfies-prop 1000
        (prop/for-all [x (fg/reference)]
          (= (->> x
                  fhir-spec/unform-xml
                  (s2/conform :fhir.xml/Reference))
             x))))

    (testing "CBOR"
      (satisfies-prop 1000
        (prop/for-all [x (fg/reference)]
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/Reference))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Reference json))
        {}
        #fhir/Reference{}

        {:reference "Patient/1"}
        #fhir/Reference{:reference "Patient/1"}

        {:reference 1}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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
      (satisfies-prop 100
        (prop/for-all [x (fg/meta)]
          (s2/valid? :fhir/Meta x))))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Meta x))
        #fhir/Identifier{:versionId "1"})))

  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/meta)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json/Meta))
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
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor/Meta))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Meta json))
        {}
        #fhir/Meta{}

        {:versionId "1"}
        #fhir/Meta{:versionId #fhir/id"1"}

        {:versionId 1}
        ::s2/invalid

        {:lastUpdated "1970-01-01T00:00:00Z"}
        (type/meta {:lastUpdated Instant/EPOCH}))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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
  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry-search)]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json.Bundle.entry/search))
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
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor.Bundle.entry/search))
             x)))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json.Bundle.entry/search json))
        {}
        #fhir/BundleEntrySearch{}

        {:id "id-134805"}
        #fhir/BundleEntrySearch{:id "id-134805"}

        {:mode "match"}
        #fhir/BundleEntrySearch{:mode #fhir/code"match"}

        {:id 1}
        ::s2/invalid))

    (testing "CBOR"
      (are [cbor fhir] (= fhir (s2/conform :fhir.cbor.Bundle.entry/search cbor))
        {}
        #fhir/BundleEntrySearch{}

        {:id "id-134805"}
        #fhir/BundleEntrySearch{:id "id-134805"}

        {:mode "match"}
        #fhir/BundleEntrySearch{:mode #fhir/code"match"}

        {:id 1}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
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
        {:score 1.1M}))

    (testing "CBOR"
      (are [fhir cbor] (= cbor (fhir-spec/parse-cbor (fhir-spec/unform-cbor fhir)))
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
  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry {:resource (fg/patient)})]
          (= (->> x
                  fhir-spec/unform-json
                  fhir-spec/parse-json
                  (s2/conform :fhir.json.Bundle/entry))
             x))))

    (testing "XML"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry {:resource (fg/patient)})]
          (= (->> x
                  (s2/unform :fhir.xml.Bundle/entry)
                  (s2/conform :fhir.xml.Bundle/entry))
             x))))

    (testing "CBOR"
      (satisfies-prop 100
        (prop/for-all [x (fg/bundle-entry {:resource (fg/patient)})]
          (= (->> x
                  fhir-spec/unform-cbor
                  fhir-spec/parse-cbor
                  (s2/conform :fhir.cbor.Bundle/entry))
             x)))))

  (testing "references"
    (satisfies-prop 10
      (prop/for-all [x (fg/bundle-entry
                         {:resource
                          (fg/observation
                            {:subject
                             (fg/reference
                               {:reference (gen/return "Patient/0")})})})]
        (empty? (type/references x))))))



;; ---- Resources -------------------------------------------------------------

(deftest patient-test
  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 100
        (prop/for-all [patient (fg/patient)]
          (= (-> patient
                 fhir-spec/unform-json
                 fhir-spec/parse-json
                 fhir-spec/conform-json)
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
          (= (-> patient
                 fhir-spec/unform-cbor
                 fhir-spec/parse-cbor
                 fhir-spec/conform-cbor)
             patient))))))


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


(deftest observation-test
  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 10
        (prop/for-all [observation (fg/observation)]
          (= (-> observation
                 fhir-spec/unform-json
                 fhir-spec/parse-json
                 fhir-spec/conform-json)
             observation))))

    (testing "XML"
      (satisfies-prop 10
        (prop/for-all [observation (fg/observation)]
          (= (-> observation
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             observation))))

    (testing "CBOR"
      (satisfies-prop 10
        (prop/for-all [observation (fg/observation)]
          (= (-> observation
                 fhir-spec/unform-cbor
                 fhir-spec/parse-cbor
                 fhir-spec/conform-cbor)
             observation)))))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/Observation
       :subject #fhir/Reference{:reference "Patient/0"}}
      [["Patient" "0"]])))


(deftest procedure-test
  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 10
        (prop/for-all [procedure (fg/procedure)]
          (= (-> procedure
                 fhir-spec/unform-json
                 fhir-spec/parse-json
                 fhir-spec/conform-json)
             procedure))))

    (testing "XML"
      (satisfies-prop 10
        (prop/for-all [procedure (fg/procedure)]
          (= (-> procedure
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             procedure))))

    (testing "CBOR"
      (satisfies-prop 10
        (prop/for-all [procedure (fg/procedure)]
          (= (-> procedure
                 fhir-spec/unform-cbor
                 fhir-spec/parse-cbor
                 fhir-spec/conform-cbor)
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
  (testing "transforming"
    (testing "JSON"
      (satisfies-prop 10
        (prop/for-all [allergy-intolerance (fg/allergy-intolerance)]
          (= (-> allergy-intolerance
                 fhir-spec/unform-json
                 fhir-spec/parse-json
                 fhir-spec/conform-json)
             allergy-intolerance))))

    (testing "XML"
      (satisfies-prop 10
        (prop/for-all [allergy-intolerance (fg/allergy-intolerance)]
          (= (-> allergy-intolerance
                 fhir-spec/unform-xml
                 fhir-spec/conform-xml)
             allergy-intolerance))))

    (testing "CBOR"
      (satisfies-prop 10
        (prop/for-all [allergy-intolerance (fg/allergy-intolerance)]
          (= (-> allergy-intolerance
                 fhir-spec/unform-cbor
                 fhir-spec/parse-cbor
                 fhir-spec/conform-cbor)
             allergy-intolerance))))))


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
  (testing "conforming"
    (s2/form :fhir.json.Task/output)
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Task json))
        {:resourceType "Task"
         :output
         [{:valueReference {:reference "bar"}}]}
        {:fhir/type :fhir/Task
         :output
         [{:fhir/type :fhir.Task/output
           :value #fhir/Reference{:reference "bar"}}]})

      (testing "bare :value properties are removed"
        (are [json fhir] (= fhir (s2/conform :fhir.json/Task json))
          {:resourceType "Task"
           :output
           [{:value {:reference "bar"}}]}
          {:fhir/type :fhir/Task
           :output
           [{:fhir/type :fhir.Task/output}]})))))

(deftest primitive-val-test
  (are [x] (fhir-spec/primitive-val? x)
    "foo"
    1
    #fhir/code"bar"))
