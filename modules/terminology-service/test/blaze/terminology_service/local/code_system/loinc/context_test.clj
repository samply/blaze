(ns blaze.terminology-service.local.code-system.loinc.context-test
  (:require
   [blaze.fhir.test-util]
   [blaze.path-spec]
   [blaze.terminology-service.local.code-system.loinc.context :as context]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest build-test
  (given (context/build)
    [:code-systems count] := 1
    [:concept-index "718-7" :display] := #fhir/string"Hemoglobin [Mass/volume] in Blood"
    [:class-index count] :? #(< 100 % 1000)

    ;; this is one of ACTIVE, TRIAL, DISCOURAGED, and DEPRECATED
    [:status-index count] := 4

    ;; this is one of 1=Laboratory class; 2=Clinical class; 3=Claims attachments; 4=Surveys
    [:class-type-index count] := 4))
