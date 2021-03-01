(ns blaze.fhir.spec-test
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec-spec]
    [blaze.fhir.spec.type :as type]
    [clojure.alpha.spec :as s2]
    [clojure.data.xml.name :as xml-name]
    [clojure.data.xml.node :as xml-node]
    [clojure.data.xml.prxml :as prxml]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cuerdas.core :as str]
    [juxt.iota :refer [given]])
  (:import
    [java.time LocalTime Instant]
    [java.nio.charset StandardCharsets]))


(xml-name/alias-uri 'f "http://hl7.org/fhir")
(xml-name/alias-uri 'xhtml "http://www.w3.org/1999/xhtml")


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest resource-test
  (testing "valid"
    (are [x] (s2/valid? :fhir/Resource x)
      {:fhir/type :fhir/Condition :id "id-204446"
       :code
       (type/map->CodeableConcept
         {:coding
          [(type/map->Coding
             {:system #fhir/uri"system-204435"
              :code #fhir/code"code-204441"})]})
       :onset #fhir/dateTime"2020-01-30"
       :subject
       (type/map->Reference
         {:reference "Patient/id-145552"})
       :meta
       (type/map->Meta
         {:versionId #fhir/id"1"
          :profile [#fhir/canonical"url-164445"]})})))


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


(deftest local-ref-spec
  (is (= ["Patient" "0"] (s/conform :blaze.fhir/local-ref "Patient/0")))

  (is (s/invalid? (s/conform :blaze.fhir/local-ref "Patient/0/1"))))


(deftest patient-id
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
    (is (s/invalid? (fhir-spec/conform-json nil))))

  (testing "string"
    (is (s/invalid? (fhir-spec/conform-json "foo"))))

  (testing "invalid"
    (is (s/invalid? (fhir-spec/conform-json {:resourceType "Patient" :id 0}))))

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
            (type/map->CodeableConcept
              {:coding
               [(type/map->Coding
                  {:system #fhir/uri"http://loinc.org"
                   :code #fhir/code"39156-5"})]})}
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
            (type/map->CodeableConcept
              {:coding
               [(type/map->Coding
                  {:system #fhir/uri"http://loinc.org"
                   :code #fhir/code"39156-5"})]})}
           (conform-xml
             [::f/Observation
              [::f/code
               [::f/coding
                [::f/system {:value "http://loinc.org"}]
                [::f/code {:value "39156-5"}]]]]))))

  (testing "Patient with gender extension"
    (is (= {:fhir/type :fhir/Patient
            :gender
            (type/->ExtendedCode
              nil
              [(type/map->Extension
                 {:url "http://fhir.de/StructureDefinition/gender-amtlich-de"
                  :value
                  (type/map->Coding
                    {:system #fhir/uri"http://fhir.de/CodeSystem/gender-amtlich-de"
                     :code #fhir/code"D"
                     :display "divers"})})]
              "other")}
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
       (type/map->CodeableConcept
         {:coding
          [(type/map->Coding
             {:system #fhir/uri"http://loinc.org"
              :code #fhir/code"39156-5"})]})}
      "{\"code\":{\"coding\":[{\"system\":\"http://loinc.org\",\"code\":\"39156-5\"}]},\"resourceType\":\"Observation\"}"))

  (testing "Observation with valueQuantity"
    (are [resource json] (= json (unform-json resource))
      {:fhir/type :fhir/Observation
       :value
       (type/map->Quantity
         {:value 36.6M
          :unit "kg/m^2"
          :system #fhir/uri"http://unitsofmeasure.org"
          :code #fhir/code"kg/m2"})}
      "{\"valueQuantity\":{\"value\":36.6,\"unit\":\"kg/m^2\",\"system\":\"http://unitsofmeasure.org\",\"code\":\"kg/m2\"},\"resourceType\":\"Observation\"}")))


(defn- conform-unform-cbor [resource]
  (-> (fhir-spec/unform-cbor resource)
      (fhir-spec/parse-cbor)
      (fhir-spec/conform-cbor)))


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
       (type/map->CodeableConcept
         {:coding
          [(type/map->Coding
             {:system #fhir/uri"http://loinc.org"
              :code #fhir/code"39156-5"})]})}))

  (testing "Observation with valueQuantity"
    (are [resource] (= resource (conform-unform-cbor resource))
      {:fhir/type :fhir/Observation
       :value
       (type/map->Quantity
         {:value 36.6M
          :unit "kg/m^2"
          :system #fhir/uri"http://unitsofmeasure.org"
          :code #fhir/code"kg/m2"})})))


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
              (type/->ExtendedCode
                nil
                [(type/map->Extension
                   {:url "http://fhir.de/StructureDefinition/gender-amtlich-de"
                    :value
                    (type/map->Coding
                      {:system #fhir/uri"http://fhir.de/CodeSystem/gender-amtlich-de"
                       :code #fhir/code"D"
                       :display "divers"})})]
                "other")}))))

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


(deftest fhir-type
  (testing "Patient"
    (is (= :fhir/Patient
           (fhir-spec/fhir-type
             (fhir-spec/conform-json {:resourceType "Patient"}))))))


(deftest explain-data-json
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


(deftest explain-data-xml
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


(deftest primitive?
  (are [spec] (fhir-spec/primitive? spec)
    :fhir/id))



;; ---- Primitive Types -------------------------------------------------------

(deftest fhir-decimal
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


(deftest fhir-base64Binary
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
      (type/->Instant "2015-02-07T13:28:17.239+02:00")))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/instant s)
      "2015-02-07T13:28:17.239+02:00"))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/instant json))
        "2015-02-07T13:28:17.239+02:00"
        (type/->Instant "2015-02-07T13:28:17.239+02:00"))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (unform-json fhir))
        (type/->Instant "2015-02-07T13:28:17.239+02:00")
        "\"2015-02-07T13:28:17.239+02:00\""
        (type/->Instant "2015-02-07T13:28:17.239Z")
        "\"2015-02-07T13:28:17.239Z\""))))


(defn elem [value]
  (sexp [nil {:value value}]))


(defn ext-elem [value]
  (sexp
    [nil {:value value}
     [:extension {:url "foo"}
      [:valueString {:value "bar"}]]]))


(deftest fhir-date
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
    nil [(type/map->Extension {:url "foo" :value "bar"})] "2020"))


(deftest fhir-dateTime
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
  (type/->ExtendedCode
    nil [(type/map->Extension {:url "bar" :value "baz"})] "foo"))


(deftest fhir-code
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


(deftest fhir-id
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
    nil [(type/map->Extension {:url "foo" :value "bar"})] 1))


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
    nil [(type/map->Extension {:url "foo" :value "bar"})] 1))


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


(deftest fhir-xhtml
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


(deftest extension-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Extension x)
        (type/map->Extension {})
        (type/map->Extension {:value #fhir/code"bar"})
        (type/map->Extension {:url "foo" :value #fhir/code"bar"})))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Extension x))
        (type/map->Extension {:url 1}))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Extension json))
        {:url "foo" :valueCode "bar"}
        (type/map->Extension {:url "foo" :value #fhir/code"bar"})

        {:url "foo" :valueReference {}}
        (type/map->Extension {:url "foo" :value (type/map->Reference {})})

        {:url "foo" :valueCodeableConcept {}}
        (type/map->Extension {:url "foo" :value (type/map->CodeableConcept {})}))))

  (testing "conforming"
    (testing "CBOR"
      (are [json fhir] (= fhir (s2/conform :fhir.cbor/Extension json))
        {:url "foo" :valueCode "bar"}
        (type/map->Extension {:url "foo" :value #fhir/code"bar"})

        {:url "foo" :valueReference {}}
        (type/map->Extension {:url "foo" :value (type/map->Reference {})})

        {:url "foo" :valueCodeableConcept {}}
        (type/map->Extension {:url "foo" :value (type/map->CodeableConcept {})}))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        (type/map->Extension {})
        {}

        (type/map->Extension {:id "id-135149"})
        {:id "id-135149"}

        (type/map->Extension
          {:extension
           [(type/map->Extension {})]})
        {:extension [{}]}

        (type/map->Extension
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        {:extension [{} {}]}

        (type/map->Extension
          {:url "url-135208"})
        {:url "url-135208"}

        (type/map->Extension
          {:value #fhir/code"code-135234"})
        {:valueCode "code-135234"}

        (type/map->Extension
          {:value (type/map->CodeableConcept {})})
        {:valueCodeableConcept {}}))

    (testing "CBOR"
      (are [fhir cbor] (= cbor (fhir-spec/parse-cbor (fhir-spec/unform-cbor fhir)))
        (type/map->Extension {})
        {}

        (type/map->Extension {:id "id-135149"})
        {:id "id-135149"}

        (type/map->Extension
          {:extension
           [(type/map->Extension {})]})
        {:extension [{}]}

        (type/map->Extension
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        {:extension [{} {}]}

        (type/map->Extension
          {:url "url-135208"})
        {:url "url-135208"}

        (type/map->Extension
          {:value #fhir/code"code-135234"})
        {:valueCode "code-135234"}

        (type/map->Extension
          {:value (type/map->CodeableConcept {})})
        {:valueCodeableConcept {}}

        (type/map->Extension
          {:value {:fhir/type :fhir/Address}})
        {:valueAddress {}}

        (type/map->Extension
          {:value
           {:fhir/type :fhir/Address
            :city "foo"}})
        {:valueAddress {:city "foo"}}))))


(deftest coding-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Coding x)
        (type/map->Coding {})
        (type/map->Coding {:system #fhir/uri"foo" :code #fhir/code"bar"})))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Coding x))
        (type/map->Coding {:system "foo"}))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Coding json))
        {:system "foo" :code "bar"}
        (type/map->Coding {:system #fhir/uri"foo" :code #fhir/code"bar"}))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        (type/map->Coding {})
        {}

        (type/map->Coding {:id "id-205424"})
        {:id "id-205424"}

        (type/map->Coding
          {:extension
           [(type/map->Extension {})]})
        {:extension [{}]}

        (type/map->Coding
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        {:extension [{} {}]}

        (type/map->Coding
          {:system #fhir/uri"system-185812"})
        {:system "system-185812"}

        (type/map->Coding
          {:version "version-185951"})
        {:version "version-185951"}

        (type/map->Coding
          {:code #fhir/code"code-190226"})
        {:code "code-190226"}

        (type/map->Coding
          {:display "display-190327"})
        {:display "display-190327"}))))


(deftest codeable-concept-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/CodeableConcept x)
        (type/map->CodeableConcept {})
        (type/map->CodeableConcept {:coding []})
        (type/map->CodeableConcept {:coding [(type/map->Coding {})]})
        (type/map->CodeableConcept {:text "foo"})))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/CodeableConcept x))
        (type/map->CodeableConcept {:text 1}))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/CodeableConcept json))
        {}
        (type/map->CodeableConcept {})
        {:coding [{}]}
        (type/map->CodeableConcept {:coding [(type/map->Coding {})]})
        {:text "text-223528"}
        (type/map->CodeableConcept {:text "text-223528"}))))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        (type/map->CodeableConcept {})
        {}

        (type/map->CodeableConcept {:id "id-134927"})
        {:id "id-134927"}

        (type/map->CodeableConcept
          {:extension
           [(type/map->Extension {})]})
        {:extension [{}]}

        (type/map->CodeableConcept
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        {:extension [{} {}]}

        (type/map->CodeableConcept {:coding [(type/map->Coding {})]})
        {:coding [{}]}

        (type/map->CodeableConcept {:text "text-223528"})
        {:text "text-223528"}))))


(deftest quantity-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Quantity x)
        (type/map->Quantity {})))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Quantity x))
        (type/map->Quantity {:value "1"}))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Quantity json))
        {}
        (type/map->Quantity {})

        {:value 1M}
        (type/map->Quantity {:value 1M})

        {:value "1"}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        (type/map->Quantity {})
        {}

        (type/map->Quantity {:id "id-134908"})
        {:id "id-134908"}

        (type/map->Quantity
          {:extension
           [(type/map->Extension {})]})
        {:extension [{}]}

        (type/map->Quantity
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        {:extension [{} {}]}

        (type/map->Quantity {:value 1M})
        {:value 1}

        (type/map->Quantity {:comparator #fhir/code"code-153342"})
        {:comparator "code-153342"}

        (type/map->Quantity {:unit "string-153351"})
        {:unit "string-153351"}

        (type/map->Quantity {:system #fhir/uri"system-153337"})
        {:system "system-153337"}

        (type/map->Quantity {:code #fhir/code"code-153427"})
        {:code "code-153427"}))))


(deftest period-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Period x)
        (type/map->Period {})
        (type/map->Period {:start #fhir/dateTime"2020"})))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Period x))
        (type/map->Period {:start "2020"}))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Period json))
        {}
        (type/map->Period {})

        {:id "id-151304"}
        (type/map->Period {:id "id-151304"})

        {:extension [{}]}
        (type/map->Period {:extension [(type/map->Extension {})]})

        {:start "2020"}
        (type/map->Period {:start #fhir/dateTime"2020"})

        {:start "foo"}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        (type/map->Period {})
        {}

        (type/map->Period {:id "id-134428"})
        {:id "id-134428"}

        (type/map->Period
          {:extension
           [(type/map->Extension {})]})
        {:extension [{}]}

        (type/map->Period
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        {:extension [{} {}]}

        (type/map->Period {:start #fhir/dateTime"2020"})
        {:start "2020"}

        (type/map->Period {:end #fhir/dateTime"2020"})
        {:end "2020"}))))


(deftest identifier-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Identifier x)
        (type/map->Identifier {})
        (type/map->Identifier {:use #fhir/code"usual"})))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Identifier x))
        (type/map->Identifier {:use "usual"}))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Identifier json))
        {}
        (type/map->Identifier {})

        {:use "usual"}
        (type/map->Identifier {:use #fhir/code"usual"})

        {:use 1}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        (type/map->Identifier {})
        {}

        (type/map->Identifier {:id "id-155426"})
        {:id "id-155426"}

        (type/map->Identifier
          {:extension
           [(type/map->Extension {})]})
        {:extension [{}]}

        (type/map->Identifier
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        {:extension [{} {}]}

        (type/map->Identifier {:use #fhir/code"use-155449"})
        {:use "use-155449"}

        (type/map->Identifier {:type (type/map->CodeableConcept {})})
        {:type {}}

        (type/map->Identifier {:system #fhir/uri"system-160011"})
        {:system "system-160011"}

        (type/map->Identifier {:value "value-160034"})
        {:value "value-160034"}

        (type/map->Identifier {:period (type/map->Period {})})
        {:period {}}

        (type/map->Identifier {:assigner (type/map->Reference {})})
        {:assigner {}})))

  (testing "unforming"
    (testing "XML"
      (are [fhir xml] (= xml (fhir-spec/unform-xml fhir))
        (type/map->Identifier {})
        (sexp [])

        (type/map->Identifier {:id "id-155426"})
        (sexp [nil {:id "id-155426"}])

        (type/map->Identifier
          {:extension
           [(type/map->Extension {})]})
        (sexp [nil {} [::f/extension]])

        (type/map->Identifier
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        (sexp [nil {} [::f/extension] [::f/extension]])

        (type/map->Identifier {:use #fhir/code"use-155449"})
        (sexp [nil {} [::f/use {:value "use-155449"}]])

        (type/map->Identifier {:type (type/map->CodeableConcept {})})
        (sexp [nil {} [::f/type]])

        (type/map->Identifier {:system #fhir/uri"system-160011"})
        (sexp [nil {} [::f/system {:value "system-160011"}]])

        (type/map->Identifier {:value "value-160034"})
        (sexp [nil {} [::f/value {:value "value-160034"}]])

        (type/map->Identifier {:period (type/map->Period {})})
        (sexp [nil {} [::f/period]])

        (type/map->Identifier {:assigner (type/map->Reference {})})
        (sexp [nil {} [::f/assigner]])))))


(deftest reference-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Reference x)
        (type/map->Reference {})
        (type/map->Reference {:reference "Patient/1"})))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Reference x))
        (type/map->Reference {:reference 1}))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Reference json))
        {}
        (type/map->Reference {})

        {:reference "Patient/1"}
        (type/map->Reference {:reference "Patient/1"})

        {:reference 1}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        (type/map->Reference {})
        {}

        (type/map->Reference {:id "id-155426"})
        {:id "id-155426"}

        (type/map->Reference {:extension []})
        {:extension []}

        (type/map->Reference {:extension [(type/map->Extension {})]})
        {:extension [{}]}

        (type/map->Reference
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        {:extension [{} {}]}

        (type/map->Reference {:reference "Patient/1"})
        {:reference "Patient/1"}

        (type/map->Reference {:type #fhir/uri"type-161222"})
        {:type "type-161222"}

        (type/map->Reference {:identifier (type/map->Identifier {})})
        {:identifier {}}

        (type/map->Reference {:display "display-161314"})
        {:display "display-161314"}))))


(deftest meta-test
  (testing "FHIR spec"
    (testing "valid"
      (are [x] (s2/valid? :fhir/Meta x)
        (type/map->Meta {})
        (type/map->Meta {:versionId #fhir/id"1"})))

    (testing "invalid"
      (are [x] (not (s2/valid? :fhir/Meta x))
        (type/map->Identifier {:versionId "1"}))))

  (testing "conforming"
    (testing "JSON"
      (are [json fhir] (= fhir (s2/conform :fhir.json/Meta json))
        {}
        (type/map->Meta {})

        {:versionId "1"}
        (type/map->Meta {:versionId #fhir/id"1"})

        {:versionId 1}
        ::s2/invalid)))

  (testing "unforming"
    (testing "JSON"
      (are [fhir json] (= json (fhir-spec/parse-json (fhir-spec/unform-json fhir)))
        (type/map->Meta {})
        {}

        (type/map->Meta {:id "id-155426"})
        {:id "id-155426"}

        (type/map->Meta {:extension []})
        {:extension []}

        (type/map->Meta {:extension [(type/map->Extension {})]})
        {:extension [{}]}

        (type/map->Meta
          {:extension
           [(type/map->Extension {})
            (type/map->Extension {})]})
        {:extension [{} {}]}

        (type/map->Meta {:versionId #fhir/id"versionId-161812"})
        {:versionId "versionId-161812"}

        (type/map->Meta {:lastUpdated Instant/EPOCH})
        {:lastUpdated "1970-01-01T00:00:00Z"}

        (type/map->Meta {:source #fhir/uri"source-162704"})
        {:source "source-162704"}

        (type/map->Meta {:profile [#fhir/canonical"profile-uri-145024"]})
        {:profile ["profile-uri-145024"]}

        (type/map->Meta {:security []})
        {:security []}

        (type/map->Meta {:security [(type/map->Coding {})]})
        {:security [{}]}

        (type/map->Meta
          {:security
           [(type/map->Coding {})
            (type/map->Coding {})]})
        {:security [{} {}]}

        (type/map->Meta {:tag []})
        {:tag []}

        (type/map->Meta {:tag [(type/map->Coding {})]})
        {:tag [{}]}

        (type/map->Meta
          {:tag
           [(type/map->Coding {})
            (type/map->Coding {})]})
        {:tag [{} {}]}))))


(deftest primitive-val-test
  (are [x] (fhir-spec/primitive-val? x)
    "foo"
    1
    #fhir/code"bar"))
