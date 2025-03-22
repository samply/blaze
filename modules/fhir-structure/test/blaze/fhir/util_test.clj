(ns blaze.fhir.util-test
  (:require
   [blaze.fhir.util :as fu]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest parameters-test
  (given (fu/parameters)
    :fhir/type := :fhir/Parameters
    :parameter :? empty?)

  (given (fu/parameters "foo" #fhir/string"bar")
    :fhir/type := :fhir/Parameters
    [:parameter 0 :name] := #fhir/string"foo"
    [:parameter 0 :value] := #fhir/string"bar")

  (given (fu/parameters "foo" nil)
    :fhir/type := :fhir/Parameters
    :parameter :? empty?)

  (given (fu/parameters "foo" {:fhir/type :fhir/ValueSet})
    :fhir/type := :fhir/Parameters
    [:parameter 0 :name] := #fhir/string"foo"
    [:parameter 0 :resource] := {:fhir/type :fhir/ValueSet}))

(deftest subsetted-test
  (are [coding] (fu/subsetted? coding)
    {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
     :code #fhir/code"SUBSETTED"})

  (are [coding] (not (fu/subsetted? coding))
    {:code #fhir/code"SUBSETTED"}
    {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"}))
