(ns blaze.terminology-service.local.value-set-test
  (:require
   [blaze.terminology-service.local.value-set :as vs]
   [blaze.terminology-service.local.value-set-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest extension-params-test
  (is (= (vs/extension-params
          {:fhir/type :fhir/ValueSet
           :compose
           {:fhir/type :fhir.ValueSet/compose
            :extension
            [#fhir/Extension
              {:url "http://hl7.org/fhir/tools/StructureDefinion/valueset-expansion-param"
               :extension
               [#fhir/Extension{:url "name" :value #fhir/code "displayLanguage"}
                #fhir/Extension{:url "value" :value #fhir/code "en"}]}]}})
         {:fhir/type :fhir/Parameters,
          :parameter
          [{:fhir/type :fhir.Parameters/parameter
            :name #fhir/string "displayLanguage"
            :value #fhir/code "en"}]})))
