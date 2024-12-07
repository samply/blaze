(ns blaze.terminology-service.local.filter.is-a-test
  (:require
   [blaze.fhir.test-util]
   [blaze.terminology-service.local.filter.core :as core]
   [blaze.terminology-service.local.filter.is-a]
   [blaze.terminology-service.local.graph-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest filter-concepts-test
  (is (= (core/filter-concepts
          {:fhir/type :fhir.ValueSet.compose.include/filter
           :property #fhir/code"concept"
           :op #fhir/code"is-a"
           :value #fhir/string"code-182832"}
          [{:fhir/type :fhir.CodeSystem/concept
            :code #fhir/code"code-182832"
            :display #fhir/string"display-182717"}
           {:fhir/type :fhir.CodeSystem/concept
            :code #fhir/code"code-191445"
            :display #fhir/string"display-191448"
            :property
            [{:fhir/type :fhir.CodeSystem.concept/property
              :code #fhir/code"parent"
              :value #fhir/code"code-182832"}]}])
         [{:fhir/type :fhir.CodeSystem/concept
           :code #fhir/code"code-191445"
           :display #fhir/string"display-191448"
           :property
           [{:fhir/type :fhir.CodeSystem.concept/property
             :code #fhir/code"parent"
             :value #fhir/code"code-182832"}]}
          {:fhir/type :fhir.CodeSystem/concept
           :code #fhir/code"code-182832"
           :display #fhir/string"display-182717"
           :property
           [{:fhir/type :fhir.CodeSystem.concept/property
             :code #fhir/code"child"
             :value #fhir/code"code-191445"}]}])))
