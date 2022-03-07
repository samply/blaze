(ns blaze.fhir.spec-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec-spec]
    [blaze.fhir.spec.memory :as mem]
    [blaze.fhir.spec.type :as type]
    [blaze.test-util :as tu]
    [clojure.alpha.spec :as s2]
    [clojure.data.xml.name :as xml-name]
    [clojure.data.xml.node :as xml-node]
    [clojure.data.xml.prxml :as prxml]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as str]
    [juxt.iota :refer [given]])
  (:import
    [java.nio.charset StandardCharsets]
    [java.time Instant LocalTime]))


(xml-name/alias-uri 'f "http://hl7.org/fhir")
(xml-name/alias-uri 'xhtml "http://www.w3.org/1999/xhtml")


(st/instrument)
(tu/init-fhir-specs)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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
       :onset #fhir/dateTime"2020-01-30"})))


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


(def sexp prxml/sexp-as-element)


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
                           :display "divers"}}]
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
            :unit "kg/m^2"
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
      :x := {}))

  (testing "Patient"
    (let [patient {:resourceType "Patient"
                   :name [{:given ["given-212441"]}]}]
      (mem/print-total-layout (fhir-spec/conform-cbor patient)))))


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
            :unit "kg/m^2"
            :system #fhir/uri"http://unitsofmeasure.org"
            :code #fhir/code"kg/m2"}})))


(deftest unform-primitives-test
  (testing "time"
    (testing "json"
      (let [time (LocalTime/of 17 23)]
        (is (identical? time (s2/unform :fhir.json/time time)))))
    (testing "cbor"
      (let [time (LocalTime/of 17 23)]
        (is (identical? time (s2/unform :fhir.cbor/time time)))))))


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

(deftest fhir-decimal-test
  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/decimal json))
        1M 1M)
      (testing "integer also conform"
        (are [json fhir] (= fhir (s2/conform :fhir.json/decimal json))
          1 1M)))
    (testing "XML"
      (are [xml fhir] (= fhir (s2/conform :fhir.xml/decimal xml))
        (sexp [nil {:value "1"}]) 1M)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        0M "0"
        1M "1"))
    (testing "XML"
      (are [fhir xml] (= xml (s2/unform :fhir.xml/decimal fhir))
        0M (sexp [nil {:value "0"}])
        1M (sexp [nil {:value "1"}])))))


(deftest fhir-base64Binary-test
  (are [s] (s2/valid? :fhir/base64Binary s)
    #fhir/base64Binary"Zm9vCg==")

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/base64Binary json))
        (str/repeat "a" 40000)
        (type/->Base64Binary (str/repeat "a" 40000))))
    (testing "XML"
      (are [xml fhir] (= fhir (s2/conform :fhir.xml/base64Binary xml))
        (xml-node/element :foo {:value (str/repeat "a" 40000)})
        (type/->Base64Binary (str/repeat "a" 40000)))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        #fhir/base64Binary"Zm9vCg==" "\"Zm9vCg==\""))
    (testing "XML"
      (are [fhir xml] (= xml (s2/unform :fhir.xml/decimal fhir))
        #fhir/base64Binary"Zm9vCg==" (sexp [nil {:value "Zm9vCg=="}])))))


(deftest fhir-instant
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/instant s)
      #fhir/instant"2015-02-07T13:28:17.239+02:00"))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/instant s)
      "2015-02-07T13:28:17.239+02:00"))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/instant json))
        "2015-02-07T13:28:17.239+02:00"
        #fhir/instant"2015-02-07T13:28:17.239+02:00")))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        #fhir/instant"2015-02-07T13:28:17.239+02:00"
        "\"2015-02-07T13:28:17.239+02:00\""
        #fhir/instant"2015-02-07T13:28:17.239Z"
        "\"2015-02-07T13:28:17.239Z\""))))


(defn elem [value]
  (sexp [nil {:value value}]))


(defn ext-elem [value]
  (sexp
    [nil {:value value}
     [:extension {:url "foo"}
      [:valueString {:value "bar"}]]]))


(deftest fhir-date-test
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/date s)
      #fhir/date"2020"))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/date s)
      "2020"))

  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (is (= #fhir/date"2020" (s2/conform :fhir.json/date "2020"))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.json/date v))
          "2019-13"
          "2019-02-29")))

    (testing "XML"
      (testing "valid"
        (is (= #fhir/date"2020" (s2/conform :fhir.xml/date (elem "2020")))))

      (testing "invalid"
        (are [v] (s2/invalid? (s2/conform :fhir.xml/date (elem v)))
          "2019-13"
          "2019-02-29"))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        #fhir/date"2020"
        "\"2020\""))))


(def extended-date-time
  (type/->DateTime
    nil [#fhir/Extension{:url "foo" :value "bar"}] "2020"))


(deftest fhir-dateTime-test
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/dateTime s)
      #fhir/dateTime"2020"))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/dateTime s)
      "2020"))

  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (is (= #fhir/dateTime"2020" (s2/conform :fhir.json/dateTime "2020"))))

      (testing "invalid"
        (are [s] (s2/invalid? (s2/conform :fhir.json/dateTime s))
          "2019-13"
          "2019-02-29")))

    (testing "XML"
      (testing "value only"
        (testing "valid"
          (are [v dt] (= dt (s2/conform :fhir.xml/dateTime (elem v)))
            "2020" #fhir/dateTime"2020"))

        (testing "invalid"
          (are [v] (s2/invalid? (s2/conform :fhir.xml/dateTime (elem v)))
            "2019-13"
            "2019-02-29")))

      (testing "with extension"
        (testing "valid"
          (are [v dt] (= dt (s2/conform :fhir.xml/dateTime (ext-elem v)))
            "2020" extended-date-time))

        (testing "invalid"
          (are [v] (s2/invalid? (s2/conform :fhir.xml/dateTime (ext-elem v)))
            "2019-13"
            "2019-02-29")))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        #fhir/dateTime"2020"
        "\"2020\""))))


(def code-element
  (sexp [nil {:value "foo"}]))


(def extended-code-element
  (sexp
    [nil {:value "foo"}
     [::f/extension {:url "bar"}
      [::f/valueString {:value "baz"}]]]))


(def extended-code
  #fhir/code{:extension [#fhir/Extension{:url "bar" :value "baz"}] :value "foo"})


(deftest fhir-code-test
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/code s)
      #fhir/code"foo"))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/code s)
      "foo"))

  (testing "conforming"
    (testing "JSON"
      (is (= #fhir/code"foo" (s2/conform :fhir.json/code "foo"))))
    (testing "XML"
      (testing "value only"
        (is (= #fhir/code"foo" (s2/conform :fhir.xml/code code-element))))
      (testing "with extension"
        (is (= extended-code (s2/conform :fhir.xml/code extended-code-element))))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        #fhir/code"foo"
        "\"foo\""))
    (testing "XML"
      (testing "value only"
        (is (= code-element (s2/unform :fhir.xml/code #fhir/code"foo"))))
      (testing "with extension"
        (is (= extended-code-element (s2/unform :fhir.xml/code extended-code)))))))


(deftest fhir-id-test
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/id s)
      #fhir/id"."
      #fhir/id"-"
      #fhir/id"a"
      #fhir/id"A"
      #fhir/id"0"))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/id s)
      "."
      "-"
      "a"
      "A"
      "0"))

  (testing "conforming from JSON to FHIR"
    (is (= #fhir/id"foo" (s2/conform :fhir.json/id "foo"))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        #fhir/id"foo"
        "\"foo\""))))


(def unsignedInt-element
  (sexp [nil {:value "1"}]))


(def extended-unsignedInt-element
  (sexp
    [nil {:value "1"}
     [::f/extension {:url "foo"}
      [::f/valueString {:value "bar"}]]]))


(def extended-unsignedInt
  (type/->ExtendedUnsignedInt
    nil [#fhir/Extension{:url "foo" :value "bar"}] 1))


(deftest fhir-unsignedInt
  (testing "FHIR spec"
    (are [i] (s2/valid? :fhir/unsignedInt i)
      #fhir/unsignedInt 1))

  (testing "JSON spec"
    (are [i] (s2/valid? :fhir.json/unsignedInt i)
      1))

  (testing "XML spec"
    (are [i] (s2/valid? :fhir.xml/unsignedInt i)
      unsignedInt-element))

  (testing "conforming"
    (testing "JSON"
      (is (= #fhir/unsignedInt 1 (s2/conform :fhir.json/unsignedInt 1))))
    (testing "XML"
      (testing "value only"
        (is (= #fhir/unsignedInt 1 (s2/conform :fhir.xml/unsignedInt unsignedInt-element))))
      (testing "with extension"
        (is (= extended-unsignedInt (s2/conform :fhir.xml/unsignedInt extended-unsignedInt-element))))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        #fhir/unsignedInt 1
        "1"))
    (testing "XML"
      (testing "value only"
        (is (= unsignedInt-element (s2/unform :fhir.xml/unsignedInt #fhir/unsignedInt 1))))
      (testing "with extension"
        (is (= extended-unsignedInt-element (s2/unform :fhir.xml/unsignedInt extended-unsignedInt)))))))


(def positiveInt-element
  (sexp [nil {:value "1"}]))


(def extended-positiveInt-element
  (sexp
    [nil {:value "1"}
     [::f/extension {:url "foo"}
      [::f/valueString {:value "bar"}]]]))


(def extended-positiveInt
  (type/->ExtendedPositiveInt
    nil [#fhir/Extension{:url "foo" :value "bar"}] 1))


(deftest fhir-positiveInt
  (testing "FHIR spec"
    (are [i] (s2/valid? :fhir/positiveInt i)
      #fhir/positiveInt 1))

  (testing "JSON spec"
    (are [i] (s2/valid? :fhir.json/positiveInt i)
      1))

  (testing "XML spec"
    (are [i] (s2/valid? :fhir.xml/positiveInt i)
      positiveInt-element))

  (testing "conforming"
    (testing "JSON"
      (is (= #fhir/positiveInt 1 (s2/conform :fhir.json/positiveInt 1))))
    (testing "XML"
      (testing "value only"
        (is (= #fhir/positiveInt 1 (s2/conform :fhir.xml/positiveInt positiveInt-element))))
      (testing "with extension"
        (is (= extended-positiveInt (s2/conform :fhir.xml/positiveInt extended-positiveInt-element))))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        #fhir/positiveInt 1
        "1"))
    (testing "XML"
      (testing "value only"
        (is (= positiveInt-element (s2/unform :fhir.xml/positiveInt #fhir/positiveInt 1))))
      (testing "with extension"
        (is (= extended-positiveInt-element (s2/unform :fhir.xml/positiveInt extended-positiveInt)))))))


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


(deftest attachment-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Attachment x)
        #fhir/Attachment{}
        #fhir/Attachment{:contentType #fhir/code"text/plain"}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Attachment x))
        #fhir/Attachment{:contentType "text/plain"})))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Attachment json))
        {}
        #fhir/Attachment{}

        {:contentType "text/plain"}
        #fhir/Attachment{:contentType #fhir/code"text/plain"}

        {:contentType 1}
        ::s2/invalid)))

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

        #fhir/Attachment{:contentType #fhir/code"text/plain"}
        {:contentType "text/plain"}

        #fhir/Attachment{:language #fhir/code"de"}
        {:language "de"}

        #fhir/Attachment{:data #fhir/base64Binary"MTA1NjE0Cg=="}
        {:data "MTA1NjE0Cg=="}

        #fhir/Attachment{:url #fhir/url"url-210424"}
        {:url "url-210424"}

        #fhir/Attachment{:size #fhir/unsignedInt 1}
        {:size 1}

        #fhir/Attachment{:hash #fhir/base64Binary"MTA1NjE0Cg=="}
        {:hash "MTA1NjE0Cg=="}

        #fhir/Attachment{:title "title-210622"}
        {:title "title-210622"}

        #fhir/Attachment{:creation #fhir/dateTime"2021"}
        {:creation "2021"}))

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

        #fhir/Attachment{:contentType #fhir/code"text/plain"}
        (sexp [nil {} [::f/contentType {:value "text/plain"}]])

        #fhir/Attachment{:language #fhir/code"de"}
        (sexp [nil {} [::f/language {:value "de"}]])

        #fhir/Attachment{:data #fhir/base64Binary"MTA1NjE0Cg=="}
        (sexp [nil {} [::f/data {:value "MTA1NjE0Cg=="}]])

        #fhir/Attachment{:url #fhir/url"url-210424"}
        (sexp [nil {} [::f/url {:value "url-210424"}]])

        #fhir/Attachment{:size #fhir/unsignedInt 1}
        (sexp [nil {} [::f/size {:value "1"}]])

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
          1))))

  (testing "conformed instance size"
    (testing "JSON"
      (testing "two interned instances take the same memory as one"
        (is (= (mem/total-size "foo")
               (mem/total-size
                 (s2/conform :fhir.json.Extension/url (String. "foo"))
                 (s2/conform :fhir.json.Extension/url (String. "foo")))))))
    
    (testing "CBOR"
      (testing "two interned instances take the same memory as one"
        (is (= (mem/total-size "foo")
               (mem/total-size
                 (s2/conform :fhir.cbor.Extension/url (String. "foo"))
                 (s2/conform :fhir.cbor.Extension/url (String. "foo")))))))))


(deftest extension-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Extension x)
        #fhir/Extension{}
        #fhir/Extension{:value #fhir/code"bar"}
        #fhir/Extension{:url "foo" :value #fhir/code"bar"}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Extension x))
        #fhir/Extension{:url 1})))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Extension json))
        {:url "foo" :valueCode "bar"}
        #fhir/Extension{:url "foo" :value #fhir/code"bar"}

        {:url "foo" :valueReference {}}
        #fhir/Extension{:url "foo" :value #fhir/Reference{}}

        {:url "foo" :valueCodeableConcept {}}
        #fhir/Extension{:url "foo" :value #fhir/CodeableConcept{}}))

    (testing "CBOR"
      (are [json fhir] (= fhir (s2/conform :fhir.cbor/Extension json))
        {:url "foo" :valueCode "bar"}
        #fhir/Extension{:url "foo" :value #fhir/code"bar"}

        {:url "foo" :valueReference {}}
        #fhir/Extension{:url "foo" :value #fhir/Reference{}}

        {:url "foo" :valueCodeableConcept {}}
        #fhir/Extension{:url "foo" :value #fhir/CodeableConcept{}})))

  (testing "conformed instance size"
    (testing "JSON"
      (are [json size] (= size (mem/total-size (s2/conform :fhir.json/Extension json)))
        {} 48
        {:url "foo" :valueCode "bar"} 160)

      (testing "two instances have only the 48 byte instance overhead"
        (is (= (+ (mem/total-size
                    (s2/conform :fhir.json/Extension
                                {:url "foo" :valueCode "bar"}))
                  48)
               (mem/total-size
                     (s2/conform :fhir.json/Extension
                                 {:url (String. "foo") :valueCode "bar"})
                     (s2/conform :fhir.json/Extension
                                 {:url (String. "foo") :valueCode "bar"})))))))

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

        #fhir/Extension{:value #fhir/CodeableConcept{:text "text-104840"}}
        {:valueCodeableConcept {:text "text-104840"}}

        #fhir/Extension
            {:value
             #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding{:system #fhir/uri"system-105127"}]}}
        {:valueCodeableConcept {:coding [{:system "system-105127"}]}}

        #fhir/Extension{:value {:fhir/type :fhir/Annotation :text "text-105422"}}
        {:valueAnnotation {:text "text-105422"}}))

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
        {:valueAddress {:city "foo"}}))))


(deftest coding-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Coding x)
        #fhir/Coding{}
        #fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Coding x))
        #fhir/Coding{:system "foo"})))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Coding json))
        {:system "foo" :code "bar"}
        #fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"})))

  (testing "conformed instance size"
    (testing "JSON"
      (are [json size] (= size (mem/total-size (s2/conform :fhir.json/Coding json)))
        {} 56
        {:system "foo" :code "bar"} 184)

      (testing "two interned instances take the same memory as one"
        (is (= 184 (mem/total-size (s2/conform :fhir.json/Coding {:system "foo" :code "bar"})
                                   (s2/conform :fhir.json/Coding {:system "foo" :code "bar"})))))))

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
      (are [x] (s2/valid? :fhir/CodeableConcept x)
        #fhir/CodeableConcept{}
        #fhir/CodeableConcept{:coding []}
        #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
        #fhir/CodeableConcept{:text "foo"}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/CodeableConcept x))
        #fhir/CodeableConcept{:text 1})))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/CodeableConcept json))
        {}
        #fhir/CodeableConcept{}
        {:coding [{}]}
        #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
        {:text "text-223528"}
        #fhir/CodeableConcept{:text "text-223528"})))

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

        #fhir/CodeableConcept{:text "text-223528"}
        {:text "text-223528"}))))


(deftest quantity-unit-test
  (testing "conforming"
    (testing "JSON"
      (testing "valid"
        (are [json fhir] (= fhir (s2/conform :fhir.json.Quantity/unit json))
          " " " "
          "unit-103640" "unit-103640"))

      (testing "invalid"
        (are [json] (not (s2/valid? :fhir.json.Quantity/unit json))
          ""
          1))))

  (testing "conformed instance size"
    (testing "JSON"
      (testing "two interned instances take the same memory as one"
        (is (= (mem/total-size "foo")
               (mem/total-size
                 (s2/conform :fhir.json.Quantity/unit (String. "foo"))
                 (s2/conform :fhir.json.Quantity/unit (String. "foo")))))))

    (testing "CBOR"
      (testing "two interned instances take the same memory as one"
        (is (= (mem/total-size "foo")
               (mem/total-size
                 (s2/conform :fhir.cbor.Quantity/unit (String. "foo"))
                 (s2/conform :fhir.cbor.Quantity/unit (String. "foo")))))))))


(deftest quantity-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Quantity x)
        #fhir/Quantity{}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Quantity x))
        #fhir/Quantity{:value "1"})))

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

        #fhir/Quantity{:unit "string-153351"}
        {:unit "string-153351"}

        #fhir/Quantity{:system #fhir/uri"system-153337"}
        {:system "system-153337"}

        #fhir/Quantity{:code #fhir/code"code-153427"}
        {:code "code-153427"}))))


(deftest period-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Period x)
        #fhir/Period{}
        #fhir/Period{:start #fhir/dateTime"2020"}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Period x))
        #fhir/Period{:start "2020"})))

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
      (are [x] (s2/valid? :fhir/Identifier x)
        #fhir/Identifier{}
        #fhir/Identifier{:use #fhir/code"usual"}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Identifier x))
        #fhir/Identifier{:use "usual"})))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Identifier json))
        {}
        #fhir/Identifier{}

        {:use "usual"}
        #fhir/Identifier{:use #fhir/code"usual"}

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
      (are [x] (s2/valid? :fhir/HumanName x)
        #fhir/HumanName{}
        #fhir/HumanName{:use #fhir/code"usual"}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/HumanName x))
        #fhir/HumanName{:use "usual"})))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/HumanName json))
        {}
        #fhir/HumanName{}

        {:use "usual"}
        #fhir/HumanName{:use #fhir/code"usual"}

        {:given ["given-212441"]}
        #fhir/HumanName{:given ["given-212441"]}

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

  (testing "conformed instance size"
    (testing "JSON"
      (are [json size] (= size (mem/total-size (s2/conform :fhir.json/HumanName json)))
        {} 64
        {:use "usual"} 128
        {:given ["given-212441"]} 184))

    (testing "CBOR"
      (are [cbor size] (= size (mem/total-size (s2/conform :fhir.cbor/HumanName cbor)))
        {} 64
        {:use "usual"} 128
        {:given ["given-212441"]} 184)))

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

        #fhir/HumanName{:given ["given-212448" "given-212454"]}
        {:given ["given-212448" "given-212454"]}

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
      (are [x] (s2/valid? :fhir/Address x)
        #fhir/Address{}
        #fhir/Address{:use #fhir/code"usual"}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Address x))
        #fhir/Address{:use "usual"})))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Address json))
        {}
        #fhir/Address{}

        {:use "usual"}
        #fhir/Address{:use #fhir/code"usual"}

        {:use 1}
        ::s2/invalid)))

  (testing "conformed instance size"
    (are [json size] (= size (mem/total-size (s2/conform :fhir.json/Address json)))
      {} 80
      {:extension [{:url "foo1foo1" :valueCode "bar"}]} 304
      {:extension [{:url (String. "foo") :valueCode (String. "bar")}
                   {:url (String. "foo") :valueCode (String. "bar")}]} 352
      {:text "text-212402"} 136
      {:line ["line-212441"]} 200))

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
      (are [x] (s2/valid? :fhir/Reference x)
        #fhir/Reference{}
        #fhir/Reference{:reference "Patient/1"}))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Reference x))
        #fhir/Reference{:reference 1})))

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

        #fhir/Reference{:extension []}
        {:extension []}

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
      (are [x] (s2/valid? :fhir/Meta x)
        #fhir/Meta{}
        #fhir/Meta{:versionId #fhir/id"1"}
        (type/mk-meta {:lastUpdated Instant/EPOCH})))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Meta x))
        #fhir/Identifier{:versionId "1"})))

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
        (type/mk-meta {:lastUpdated Instant/EPOCH}))))

  (testing "conformed instance size"
    (are [json size] (= size (mem/total-size (s2/conform :fhir.json/Meta json)))
      {} 64
      {:versionId "1"} 128
      {:profile ["foo"]} 192)

    (testing "two interned instances take the same memory as one"
      (is (= 192 (mem/total-size (s2/conform :fhir.json/Meta {:profile ["foo"]})
                                 (s2/conform :fhir.json/Meta {:profile ["foo"]}))))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        #fhir/Meta{}
        {}

        #fhir/Meta{:id "id-155426"}
        {:id "id-155426"}

        #fhir/Meta{:extension []}
        {:extension []}

        #fhir/Meta{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/Meta{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/Meta{:versionId #fhir/id"versionId-161812"}
        {:versionId "versionId-161812"}

        (type/mk-meta {:lastUpdated Instant/EPOCH})
        {:lastUpdated "1970-01-01T00:00:00Z"}

        #fhir/Meta{:source #fhir/uri"source-162704"}
        {:source "source-162704"}

        #fhir/Meta{:profile [#fhir/canonical"profile-uri-145024"]}
        {:profile ["profile-uri-145024"]}

        #fhir/Meta{:security []}
        {:security []}

        #fhir/Meta{:security [#fhir/Coding{}]}
        {:security [{}]}

        #fhir/Meta{:security [#fhir/Coding{} #fhir/Coding{}]}
        {:security [{} {}]}

        #fhir/Meta{:tag []}
        {:tag []}

        #fhir/Meta{:tag [#fhir/Coding{}]}
        {:tag [{}]}

        #fhir/Meta{:tag [#fhir/Coding{} #fhir/Coding{}]}
        {:tag [{} {}]}))))


(deftest bundle-entry-search-test
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

        #fhir/BundleEntrySearch{:extension []}
        {:extension []}

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

        #fhir/BundleEntrySearch{:extension []}
        {:extension []}

        #fhir/BundleEntrySearch{:extension [#fhir/Extension{}]}
        {:extension [{}]}

        #fhir/BundleEntrySearch{:extension [#fhir/Extension{} #fhir/Extension{}]}
        {:extension [{} {}]}

        #fhir/BundleEntrySearch{:mode #fhir/code"match"}
        {:mode "match"}

        #fhir/BundleEntrySearch{:score 1.1M}
        {:score 1.1M}))))


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
  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/Observation
       :subject #fhir/Reference{:reference "Patient/0"}}
      [["Patient" "0"]])))


(deftest provenance-test
  (testing "references"
    (are [x refs] (= refs (type/references x))
      {:fhir/type :fhir/Provenance
       :target
       [#fhir/Reference{:reference "Patient/204750"}
        #fhir/Reference{:reference "Observation/204754"}]}
      [["Patient" "204750"]
       ["Observation" "204754"]])))


(deftest primitive-val-test
  (are [x] (fhir-spec/primitive-val? x)
    "foo"
    1
    #fhir/code"bar"))
