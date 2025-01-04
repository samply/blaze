(ns blaze.fhir.util-test
  (:require
   [blaze.fhir.util :as u]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest parameters-test
  (given (u/parameters)
    :fhir/type := :fhir/Parameters
    :parameter :? empty?)

  (given (u/parameters "foo" #fhir/string"bar")
    :fhir/type := :fhir/Parameters
    [:parameter 0 :name] := #fhir/string"foo"
    [:parameter 0 :value] := #fhir/string"bar")

  (given (u/parameters "foo" nil)
    :fhir/type := :fhir/Parameters
    :parameter :? empty?)

  (given (u/parameters "foo" {:fhir/type :fhir/ValueSet})
    :fhir/type := :fhir/Parameters
    [:parameter 0 :name] := #fhir/string"foo"
    [:parameter 0 :resource] := {:fhir/type :fhir/ValueSet}))
