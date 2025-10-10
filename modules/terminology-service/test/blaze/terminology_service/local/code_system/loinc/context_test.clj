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
    [:concept-index "718-7" :display] := #fhir/string "Hemoglobin [Mass/volume] in Blood"
    [:concept-index "718-7" :loinc/properties :component] := ["HEMOGLOBIN" "LP14449-0"]
    [:concept-index "718-7" :loinc/properties :property] := ["MCNC" "LP6827-2"]
    [:concept-index "718-7" :loinc/properties :time] := ["PT" "LP6960-1"]
    [:concept-index "718-7" :loinc/properties :system] := ["BLD" "LP7057-5"]
    [:concept-index "718-7" :loinc/properties :scale] := ["QN" "LP7753-9"]
    [:concept-index "718-7" :loinc/properties #(contains? % :method)] := false
    [:concept-index "718-7" :loinc/properties :class] := ["HEM/BC" "LP7803-2"]
    [:concept-index "718-7" :loinc/properties :class-type] := :laboratory-class
    [:concept-index "718-7" :loinc/properties :order-obs] := :both

    [:concept-index "18868-0" :loinc/properties :property] := ["SUSC" "LP6870-2"]
    [:concept-index "18868-0" :loinc/properties :order-obs] := :observation

    [:concept-index "100987-7" :loinc/properties :order-obs] := :order

    [:concept-index "LA26421-0" :display] := #fhir/string "Consider alternative medication"

    [:component-index count] :? #(< 10000 %)
    [:component-index #(get % nil) count] := 0
    [:component-index "" count] := 0
    [:component-index "-" count] := 0

    [:property-index count] :? #(< 100 % 1000)
    [:property-index #(get % nil) count] := 0
    [:property-index "" count] := 0
    [:property-index "-" count] := 0
    [:property-index "SUSC" count] :? #(< 1000 % 10000)
    [:property-index "LP6870-2" count] :? #(< 1000 % 10000)

    [:time-index count] :? #(< 100 % 1000)
    [:time-index #(get % nil) count] := 0
    [:time-index "" count] := 0
    [:time-index "-" count] := 0

    [:system-index count] :? #(< 1000 % 10000)
    [:system-index #(get % nil) count] := 0
    [:system-index "" count] := 0
    [:system-index "-" count] := 0

    [:scale-index count] :? #(< 10 % 100)
    [:scale-index #(get % nil) count] := 0
    [:scale-index "" count] := 0
    [:scale-index "-" count] := 0

    [:method-index count] :? #(< 1000 % 10000)
    [:method-index #(get % nil) count] := 0
    [:method-index "" count] := 0
    [:method-index "-" count] := 0
    [:method-index "GENOTYPING" count] :? #(< 100 % 1000)

    [:class-index count] :? #(< 100 % 1000)
    [:class-index #(get % nil) count] := 0
    [:class-index "" count] := 0
    [:class-index "-" count] := 0
    [:class-index "CYTO" count] :? #(< 10 % 100)
    [:class-index "LP7789-3" count] :? #(< 10 % 100)

    ;; this is one of ACTIVE, TRIAL, DISCOURAGED, and DEPRECATED
    [:status-index count] := 4

    ;; this is one of 1=Laboratory class; 2=Clinical class; 3=Claims attachments; 4=Surveys
    [:class-type-index count] := 4

    [:order-obs-index count] := 4

    ;; Parts
    [:component "URIDINE"] := "LP99932-3"
    [:property "SUSC"] := "LP6870-2"
    [:time "10H"] := "LP6903-1"
    [:system "BUCCAL"] := "LP94228-1"
    [:scale "ORDQN"] := "LP7752-1"
    [:method "GENOTYPING"] := "LP28723-2"
    [:class "CYTO"] := "LP7789-3"

    ;; Answer List Value Sets
    [:value-sets "LL4049-4" :title] := #fhir/string "Medication usage suggestion"
    [:value-set-concepts "LL4049-4" 0 :code] := #fhir/code "LA26421-0"))
