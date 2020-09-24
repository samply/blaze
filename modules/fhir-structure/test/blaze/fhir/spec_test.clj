(ns blaze.fhir.spec-test
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec-spec]
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cuerdas.core :as str]
    [juxt.iota :refer [given]]))


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


(deftest fhir-id
  (are [s] (s2/valid? :fhir/id s)
    "."
    "-"
    "a"
    "A"
    "0"))


(deftest patient-id
  (are [s] (s2/valid? :fhir.Patient/id s)
    "."
    "-"
    "a"
    "A"
    "0"))


(deftest base64Binary
  (testing "long base64Binary values validate"
    (is (s2/valid? :fhir/base64Binary (str/repeat "a" 40000)))))


(deftest valid?
  (testing "valid resources"
    (are [resource] (fhir-spec/valid? resource)
      {:resourceType "Patient"
       :id "."}
      {:resourceType "Patient"
       :id "0"}))

  (testing "invalid resources"
    (are [resource] (not (fhir-spec/valid? resource))
      {}
      {:resourceType "Patient"
       :id ""}
      {:resourceType "Patient"
       :id "/"})))


(deftest fhir-path
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


(deftest explain-data
  (testing "valid resources"
    (are [resource] (nil? (fhir-spec/explain-data resource))
      {:resourceType "Patient" :id "."}
      {:resourceType "Patient" :id "0"}))


  (testing "empty resource"
    (given (fhir-spec/explain-data {})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "value"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Given resource does not contain `resourceType` key!"))

  (testing "invalid resource"
    (given (fhir-spec/explain-data {:resourceType "Patient"
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
    (given (fhir-spec/explain-data {:resourceType "Patient"
                                    :contact ""})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `JSON array`."
      [:fhir/issues 0 :fhir.issues/expression] := "contact"))

  (testing "invalid non-primitive element"
    (given (fhir-spec/explain-data {:resourceType "Patient"
                                    :name ""})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value ``. Expected type is `JSON array`."
      [:fhir/issues 0 :fhir.issues/expression] := "name"))

  (testing "Include namespace part if more than fhir"
    (given (fhir-spec/explain-data {:resourceType "Patient"
                                    :contact [2]})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value `2`. Expected type is `Patient.contact`."
      [:fhir/issues 0 :fhir.issues/expression] := "contact[0]"))

  (testing "invalid non-primitive element and wrong type in list"
    (given (fhir-spec/explain-data {:resourceType "Patient"
                                    :name [1]})
      [:fhir/issues 0 :fhir.issues/severity] := "error"
      [:fhir/issues 0 :fhir.issues/code] := "invariant"
      [:fhir/issues 0 :fhir.issues/diagnostics] :=
      "Error on value `1`. Expected type is `HumanName`."
      [:fhir/issues 0 :fhir.issues/expression] := "name[0]")))


(deftest choices
  (testing "Observation.value"
    (is (= (fhir-spec/choices (:value (fhir-spec/child-specs :fhir/Observation)))
           [[:valueQuantity :fhir/Quantity]
            [:valueCodeableConcept :fhir/CodeableConcept]
            [:valueString :fhir/string]
            [:valueBoolean :fhir/boolean]
            [:valueInteger :fhir/integer]
            [:valueRange :fhir/Range]
            [:valueRatio :fhir/Ratio]
            [:valueSampledData :fhir/SampledData]
            [:valueTime :fhir/time]
            [:valueDateTime :fhir/dateTime]
            [:valuePeriod :fhir/Period]]))))


(deftest primitive?
  (are [spec] (fhir-spec/primitive? spec)
    :fhir/id))


(deftest system?
  (are [spec] (fhir-spec/system? spec)
    `string?
    `(s2/and string? #(< (count %) 10))))
