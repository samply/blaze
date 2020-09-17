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
    [juxt.iota :refer [given]]))


(xml-name/alias-uri 'f "http://hl7.org/fhir")
(xml-name/alias-uri 'xhtml "http://www.w3.org/1999/xhtml")


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest resource-id
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


(deftest valid?-test
  (testing "valid resources"
    (are [resource] (fhir-spec/valid-json? resource)
      {:resourceType "Patient"
       :id "."}
      {:resourceType "Patient"
       :id "0"}))

  (testing "invalid resources"
    (are [resource] (not (fhir-spec/valid-json? resource))
      {}
      {:resourceType "Patient"
       :id ""}
      {:resourceType "Patient"
       :id "/"})))


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
  (testing "empty patient resource"
    (testing "gets type annotated"
      (is (= :fhir/Patient
             (fhir-spec/fhir-type (fhir-spec/conform-json {:resourceType "Patient"})))))

    (testing "stays the same"
      (is (= {:fhir/type :fhir/Patient}
             (fhir-spec/conform-json {:resourceType "Patient"})))))

  (testing "deceasedBoolean on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased true}
           (fhir-spec/conform-json {:resourceType "Patient" :deceasedBoolean true}))))

  (testing "deceasedDateTime on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :deceased #fhir/dateTime"2020"}
           (fhir-spec/conform-json {:resourceType "Patient" :deceasedDateTime "2020"}))))

  (testing "multipleBirthInteger on Patient will be remapped"
    (is (= {:fhir/type :fhir/Patient :multipleBirth 2}
           (fhir-spec/conform-json {:resourceType "Patient" :multipleBirthInteger 2}))))

  (testing "Observation with code"
    (is (= {:fhir/type :fhir/Observation
            :code
            {:fhir/type :fhir/CodeableConcept
             :coding
             [{:fhir/type :fhir/Coding
               :system #fhir/uri"http://loinc.org"
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
            {:fhir/type :fhir/CodeableConcept
             :coding
             [{:fhir/type :fhir/Coding
               :system #fhir/uri"http://loinc.org"
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
            (type/->ExtendedCode
              nil
              [{:fhir/type :fhir/Extension
                :url "http://fhir.de/StructureDefinition/gender-amtlich-de"
                :value
                {:fhir/type :fhir/Coding
                 :system #fhir/uri"http://fhir.de/CodeSystem/gender-amtlich-de"
                 :code #fhir/code"D"
                 :display "divers"}}]
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

(comment
  (s2/form :fhir.xml.Questionnaire/item)
  (s2/form :fhir.json.Questionnaire/item)
  )


(defn remove-narrative [entry]
  (update entry :resource dissoc :text))


(comment
  (require '[criterium.core :refer [bench quick-bench]])

  (def parsed-json-observation
    {:category [{:coding [{:code "vital-signs" :system "http://terminology.hl7.org/CodeSystem/observation-category"}]}]
     :meta {:profile ["https://fhir.bbmri.de/StructureDefinition/Bmi"]}
     :valueQuantity {:code "kg/m2" :system "http://unitsofmeasure.org" :unit "kg/m2" :value 36.6M}
     :resourceType "Observation"
     :effectiveDateTime "2005-06-17"
     :status "final"
     :id "0-bmi"
     :code {:coding [{:code "39156-5" :system "http://loinc.org"}]}
     :subject {:reference "Patient/0"}})

  (def parsed-xml-observation
    (fhir-spec/unform-xml (fhir-spec/conform-json parsed-json-observation)))

  (= (fhir-spec/conform-json parsed-json-observation)
     (fhir-spec/conform-xml parsed-xml-observation))

  ;; 17 µs
  (quick-bench (fhir-spec/conform-json parsed-json-observation))

  ;; 46 µs
  (quick-bench (fhir-spec/conform-xml parsed-xml-observation))

  ;; 11 µs
  (quick-bench
    (fhir-spec/conform-cbor
      {:category [{:coding [{:code "vital-signs" :system "http://terminology.hl7.org/CodeSystem/observation-category"}]}]
       :meta {:profile ["https://fhir.bbmri.de/StructureDefinition/Bmi"]}
       :valueQuantity {:code "kg/m2" :system "http://unitsofmeasure.org" :unit "kg/m2" :value 36.6M}
       :resourceType "Observation"
       :effectiveDateTime "2005-06-17"
       :status "final"
       :id "0-bmi"
       :code {:coding [{:code "39156-5" :system "http://loinc.org"}]}
       :subject {:reference "Patient/0"}}))

  (require '[cheshire.core :as json]
           '[cheshire.parse :refer [*use-bigdecimals?*]])

  (def data
    (json/generate-cbor
      {:category [{:coding [{:code "vital-signs" :system "http://terminology.hl7.org/CodeSystem/observation-category"}]}]
       :meta {:profile ["https://fhir.bbmri.de/StructureDefinition/Bmi"]}
       :valueQuantity {:code "kg/m2" :system "http://unitsofmeasure.org" :unit "kg/m2" :value 36.6M}
       :resourceType "Observation"
       :effectiveDateTime "2005-06-17"
       :status "final"
       :id "0-bmi"
       :code {:coding [{:code "39156-5" :system "http://loinc.org"}]}
       :subject {:reference "Patient/0"}}))

  (quick-bench (json/parse-cbor data keyword))

  ;; 20 µs
  (quick-bench (fhir-spec/conform-cbor (json/parse-cbor data keyword)))

  (def clementine-json
    (-> (binding [*use-bigdecimals?* true]
          (json/parse-string
            (slurp "/Users/akiel/coding/synthea/output/fhir/Clementine778_Heller342_983b62ad-6da5-460f-8593-e97834178d53.json") keyword))
        (update :entry (partial mapv remove-narrative))))


  (def clementine
    (fhir-spec/conform-json clementine-json))

  (= (fhir-spec/conform-xml (fhir-spec/unform-xml clementine)) clementine)

  ;; 2.7 s - 2.27 s
  (st/unstrument)
  (quick-bench (fhir-spec/unform-xml clementine))
  (dotimes [_ 10]
    (fhir-spec/unform-xml clementine))

  (-> clementine :entry first :resource)
  )


(deftest unform-json-test
  (testing "Patient with deceasedBoolean"
    (let [json {:resourceType "Patient" :deceasedBoolean true}]
      (is (= json (fhir-spec/unform-json (fhir-spec/conform-json json))))))

  (testing "Patient with deceasedDateTime"
    (let [json {:resourceType "Patient" :deceasedDateTime "2020"}]
      (is (= json (fhir-spec/unform-json (fhir-spec/conform-json json))))))

  (testing "Patient with multipleBirthBoolean"
    (let [json {:resourceType "Patient" :multipleBoolean false}]
      (is (= json (fhir-spec/unform-json (fhir-spec/conform-json json))))))

  (testing "Patient with multipleBirthInteger"
    (let [json {:resourceType "Patient" :multipleBirthInteger 2}]
      (is (= json (fhir-spec/unform-json {:fhir/type :fhir/Patient :multipleBirth (int 2)}))))))


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
                [{:fhir/type :fhir/Extension
                  :url "http://fhir.de/StructureDefinition/gender-amtlich-de"
                  :value
                  {:fhir/type :fhir/Coding
                   :system #fhir/uri"http://fhir.de/CodeSystem/gender-amtlich-de"
                   :code #fhir/code"D"
                   :display "divers"}}]
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
           (fhir-spec/fhir-type (fhir-spec/conform-json {:resourceType "Patient"}))))))


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
    (given (fhir-spec/explain-data-json {:resourceType "Patient"
                                         :name [{:use "" :text []}]})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      [:fhir/issues 0 :fhir.issues/expression] := "name[0].use"
      [:fhir/issues 1 :fhir.issues/severity] := "error"
      [:fhir/issues 1 :fhir.issues/code] := "invariant"
      [:fhir/issues 1 :fhir.issues/diagnostics] :=
      "Error on value `[]`. Expected type is `string`."
      [:fhir/issues 1 :fhir.issues/expression] := "name[0].text"))

  (testing "invalid backbone-element"
    (given (fhir-spec/explain-data-json {:resourceType "Patient"
                                         :contact ""})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `JSON array`."
      [:fhir/issues 0 :fhir.issues/expression] := "contact"))

  (testing "invalid non-primitive element"
    (given (fhir-spec/explain-data-json {:resourceType "Patient"
                                         :name ""})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `JSON array`."
      [:fhir/issues 0 :fhir.issues/expression] := "name"))

  (testing "Include namespace part if more than fhir"
    (given (fhir-spec/explain-data-json {:resourceType "Patient"
                                         :contact [2]})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value `2`. Expected type is `Patient.contact`."
      [:fhir/issues 0 :fhir.issues/expression] := "contact[0]"))

  (testing "invalid non-primitive element and wrong type in list"
    (given (fhir-spec/explain-data-json {:resourceType "Patient"
                                         :name [1]})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value `1`. Expected type is `HumanName`."
      [:fhir/issues 0 :fhir.issues/expression] := "name[0]")))


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
      [:fhir/issues 0 :fhir.issues/diagnostics] := "Unknown resource type `<unknown>`."))

  (testing "invalid resource"
    (given (fhir-spec/explain-data-xml
             (sexp [::f/Patient [::f/name [::f/use {:value ""}]]]))
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      ;; TODO: implement expression for XML
      (comment [:fhir/issues 0 :fhir.issues/expression] := "name[0].use"))))


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
      (are [fhir json] (= json (s2/unform :fhir.json/decimal fhir))
        0M 0M
        1M 1M))
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
        (type/->Base64Binary (str/repeat "a" 40000))))))


(deftest fhir-instant
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/instant s)
      (type/->Instant "2015-02-07T13:28:17.239+02:00")))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/instant s)
      "2015-02-07T13:28:17.239+02:00"))

  (testing "conforming from JSON to FHIR"
    (is (= (type/->Instant "2015-02-07T13:28:17.239+02:00")
           (s2/conform :fhir.json/instant "2015-02-07T13:28:17.239+02:00"))))

  (testing "unforming from FHIR to JSON"
    (is (= "2015-02-07T13:28:17.239+02:00"
           (s2/unform :fhir.json/instant (type/->Instant "2015-02-07T13:28:17.239+02:00"))))))


(deftest fhir-date
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/date s)
      #fhir/date"2020"))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/date s)
      "2020"))

  (testing "conforming from JSON to FHIR"
    (is (= #fhir/date"2020" (s2/conform :fhir.json/date "2020"))))

  (testing "unforming from FHIR to JSON"
    (is (= "2020" (s2/unform :fhir.json/date #fhir/date"2020")))))


(def date-time-element
  (sexp [nil {:value "2020"}]))


(def extended-date-time-element
  (sexp
    [nil {:value "2020"}
     [:extension {:url "foo"}
      [:valueString {:value "bar"}]]]))


(def extended-date-time
  (type/->DateTime
    nil [{:fhir/type :fhir/Extension :url "foo" :value "bar"}] "2020"))


(deftest fhir-dateTime
  (testing "FHIR spec"
    (are [s] (s2/valid? :fhir/dateTime s)
      #fhir/dateTime"2020"))

  (testing "JSON spec"
    (are [s] (s2/valid? :fhir.json/dateTime s)
      "2020"))

  (testing "conforming"
    (testing "JSON"
      (is (= #fhir/dateTime"2020" (s2/conform :fhir.json/dateTime "2020"))))
    (testing "XML"
      (testing "value only"
        (is (= #fhir/dateTime"2020" (s2/conform :fhir.xml/dateTime date-time-element))))
      (testing "with extension"
        (is (= extended-date-time (s2/conform :fhir.xml/dateTime extended-date-time-element))))))

  (testing "unforming"
    (testing "JSON"
      (is (= "2020" (s2/unform :fhir.json/dateTime #fhir/dateTime"2020"))))))


(def code-element
  (sexp [nil {:value "foo"}]))


(def extended-code-element
  (sexp
    [nil {:value "foo"}
     [::f/extension {:url "bar"}
      [::f/valueString {:value "baz"}]]]))


(def extended-code
  (type/->ExtendedCode
    nil [{:fhir/type :fhir/Extension :url "bar" :value "baz"}] "foo"))


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
      (is (= "foo" (s2/unform :fhir.json/code #fhir/code"foo"))))
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

  (testing "unforming from FHIR to JSON"
    (is (= "foo" (s2/unform :fhir.json/id #fhir/id"foo")))))


(def unsignedInt-element
  (sexp [nil {:value "1"}]))


(def extended-unsignedInt-element
  (sexp
    [nil {:value "1"}
     [::f/extension {:url "foo"}
      [::f/valueString {:value "bar"}]]]))


(def extended-unsignedInt
  (type/->ExtendedUnsignedInt
    nil [{:fhir/type :fhir/Extension :url "foo" :value "bar"}] 1))


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
      (is (= 1 (s2/unform :fhir.json/unsignedInt #fhir/unsignedInt 1))))
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
    nil [{:fhir/type :fhir/Extension :url "foo" :value "bar"}] 1))


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
      (is (= 1 (s2/unform :fhir.json/positiveInt #fhir/positiveInt 1))))
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
      (is (= "foo" (s2/unform :fhir.json/xhtml #fhir/xhtml"foo"))))
    (testing "XML"
      (is (= xhtml-element
             (s2/unform :fhir.xml/xhtml #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"))))))
