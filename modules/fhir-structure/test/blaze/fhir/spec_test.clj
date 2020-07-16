(ns blaze.fhir.spec-test
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec-spec]
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]))


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


(deftest valid?
  (testing "valid resources"
    (are [resource] (fhir-spec/valid? resource)
      {:resourceType "Patient"
       :id "."}
      {:resourceType "Patient"
       :id "0"}))

  (testing "invalid resources"
    (are [resource] (not (fhir-spec/valid? resource))
      {:resourceType "Patient"
       :id ""}
      {:resourceType "Patient"
       :id "/"})))


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
