(ns blaze.terminology-service.local.value-set.vcl-test
  (:require
   [blaze.terminology-service.local.value-set.vcl :as vcl]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest parse-expr-test
  (given (vcl/parse-expr "(http://loinc.org)")
    ::anom/category := ::anom/incorrect
    ::anom/message := "Invalid VCL expression `(http://loinc.org)`. Expected code, codeList, STAR, URI or filterList at position 18")

  (testing "all codes"
    (given (vcl/parse-expr "(http://loinc.org)*")
      :fhir/type := :fhir/ValueSet
      [:compose :include count] := 1
      [:compose :include 0 :system] := #fhir/uri "http://loinc.org"
      [:compose :include 0 :filter count] := 1
      [:compose :include 0 :filter 0 :property] := #fhir/code "concept"
      [:compose :include 0 :filter 0 :op] := #fhir/code "exists"
      [:compose :include 0 :filter 0 :value] := #fhir/string "true")

    (testing "with version"
      (given (vcl/parse-expr "(http://loinc.org|2.78)*")
        :fhir/type := :fhir/ValueSet
        [:compose :include count] := 1
        [:compose :include 0 :system] := #fhir/uri "http://loinc.org|2.78"
        [:compose :include 0 :filter count] := 1
        [:compose :include 0 :filter 0 :property] := #fhir/code "concept"
        [:compose :include 0 :filter 0 :op] := #fhir/code "exists"
        [:compose :include 0 :filter 0 :value] := #fhir/string "true")))

  (testing "filter regex"
    (given (time (vcl/parse-expr "(http://loinc.org)COMPONENT/\"Hemoglobin|Amprenavir\""))
      :fhir/type := :fhir/ValueSet
      [:compose :include count] := 1
      [:compose :include 0 :system] := #fhir/uri "http://loinc.org"
      [:compose :include 0 :filter count] := 1
      [:compose :include 0 :filter 0 :property] := #fhir/code "COMPONENT"
      [:compose :include 0 :filter 0 :op] := #fhir/code "regex"
      [:compose :include 0 :filter 0 :value] := #fhir/string "Hemoglobin|Amprenavir"))

  (testing "exclusion"
    (given (time (vcl/parse-expr "(http://loinc.org)COMPONENT/\"Hemoglobin|Amprenavir\"-(http://loinc.org)26465-5"))
      :fhir/type := :fhir/ValueSet
      [:compose :include count] := 1
      [:compose :include 0 :system] := #fhir/uri "http://loinc.org"
      [:compose :include 0 :filter count] := 1
      [:compose :include 0 :filter 0 :property] := #fhir/code "COMPONENT"
      [:compose :include 0 :filter 0 :op] := #fhir/code "regex"
      [:compose :include 0 :filter 0 :value] := #fhir/string "Hemoglobin|Amprenavir"
      [:compose :exclude count] := 1
      [:compose :exclude 0 :system] := #fhir/uri "http://loinc.org"
      [:compose :exclude 0 :concept count] := 1
      [:compose :exclude 0 :concept 0 :code] := #fhir/code "26465-5"))

  (given (vcl/parse-expr "(http://hl7.org/fhir/administrative-gender)(male;female)")
    :fhir/type := :fhir/ValueSet
    [:compose :include count] := 1
    [:compose :include 0 :system] := #fhir/uri "http://hl7.org/fhir/administrative-gender"
    [:compose :include 0 :concept count] := 2
    [:compose :include 0 :concept 0 :code] := #fhir/code "male"
    [:compose :include 0 :concept 1 :code] := #fhir/code "female"))
