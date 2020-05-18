(ns blaze.fhir.spec-test
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec-spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :debug} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest local-ref-spec
  (is (= ["Patient" "0"] (s/conform :blaze.fhir/local-ref "Patient/0")))

  (is (s/invalid? (s/conform :blaze.fhir/local-ref "Patient/0/1"))))


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
