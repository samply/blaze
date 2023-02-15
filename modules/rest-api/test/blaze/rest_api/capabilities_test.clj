(ns blaze.rest-api.capabilities-test
  (:require
    [blaze.db.impl.search-param]
    [blaze.fhir.structure-definition-repo]
    [blaze.rest-api.capabilities :as capabilities]
    [blaze.rest-api.capabilities-spec]
    [blaze.test-util :as tu :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.ring]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(def ^:private copyright
  #fhir/markdown"Copyright 2019 - 2023 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.")


(defn- search-param [name]
  (fn [params] (some #(when (= name (:name %)) %) params)))


(def system
  {:blaze.fhir/structure-definition-repo {}
   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}})


(deftest capabilities-handler-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "minimal config"
      (given
        (-> @((capabilities/capabilities-handler
                {:version "version-131640"
                 :structure-definitions
                 [{:kind "resource" :name "Patient"}]
                 :search-param-registry search-param-registry})
              {:blaze/base-url "base-url-131713"})
            :body)
        :fhir/type := :fhir/CapabilityStatement
        :status := #fhir/code"active"
        :experimental := false
        :publisher := "The Samply Community"
        :copyright := copyright
        :kind := #fhir/code"instance"
        [:software :name] := "Blaze"
        [:software :version] := "version-131640"
        [:implementation :url] := #fhir/url"base-url-131713"
        :fhirVersion := #fhir/code"4.0.1"
        :format := [#fhir/code"application/fhir+json"
                    #fhir/code"application/xml+json"]
        [:rest 0 :searchParam 0 :name] := "_sort"
        [:rest 0 :searchParam 0 :type] := "special"
        [:rest 0 :searchParam 0 :documentation] := "Only `_lastUpdated` and `-_lastUpdated` is supported at the moment."))

    (testing "minimal config + search-system"
      (given
        (-> @((capabilities/capabilities-handler
                {:version "version-131640"
                 :structure-definitions
                 [{:kind "resource" :name "Patient"}]
                 :search-param-registry search-param-registry
                 :search-system-handler ::search-system})
              {})
            :body)
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :interaction 0 :code] := #fhir/code"search-system"))

    (testing "minimal config + history-system"
      (given
        (-> @((capabilities/capabilities-handler
                {:version "version-131640"
                 :structure-definitions
                 [{:kind "resource" :name "Patient"}]
                 :search-param-registry search-param-registry
                 :history-system-handler ::history-system})
              {})
            :body)
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :interaction 0 :code] := #fhir/code"history-system"))

    (testing "Patient interaction"
      (given
        (-> @((capabilities/capabilities-handler
                {:version "version-131640"
                 :structure-definitions
                 [{:kind "resource" :name "Patient"}]
                 :search-param-registry search-param-registry
                 :resource-patterns
                 [#:blaze.rest-api.resource-pattern
                         {:type "Patient"
                          :interactions
                          {:read
                           #:blaze.rest-api.interaction
                                   {:handler (fn [_])}}}]})
              {})
            :body)
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :resource 0 :type] := #fhir/code"Patient"
        [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code"read"
        [:rest 0 :resource 0 :referencePolicy] :? (partial some #{#fhir/code"enforced"})
        [:rest 0 :resource 0 :searchRevInclude 0] := "Account:patient"
        [:rest 0 :resource 0 :searchRevInclude 1] := "Account:subject"
        [:rest 0 :resource 0 :searchRevInclude 2] := "ActivityDefinition:composed-of")

      (testing "with disabled referential integrity check"
        (given
          (-> @((capabilities/capabilities-handler
                  {:version "version-131640"
                   :structure-definitions
                   [{:kind "resource" :name "Patient"}]
                   :search-param-registry search-param-registry
                   :resource-patterns
                   [#:blaze.rest-api.resource-pattern
                           {:type "Patient"
                            :interactions
                            {:read
                             #:blaze.rest-api.interaction
                                     {:handler (fn [_])}}}]
                   :enforce-referential-integrity false})
                {})
              :body)
          :fhir/type := :fhir/CapabilityStatement
          [:rest 0 :resource 0 :type] := #fhir/code"Patient"
          [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code"read"
          [:rest 0 :resource 0 :referencePolicy] :? (comp not (partial some #{#fhir/code"enforced"})))))

    (testing "Observation interaction"
      (given
        (-> @((capabilities/capabilities-handler
                {:version "version-131640"
                 :structure-definitions
                 [{:kind "resource" :name "Observation"}]
                 :search-param-registry search-param-registry
                 :resource-patterns
                 [#:blaze.rest-api.resource-pattern
                         {:type "Observation"
                          :interactions
                          {:read
                           #:blaze.rest-api.interaction
                                   {:handler (fn [_])}}}]})
              {})
            :body)
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :resource 0 :type] := #fhir/code"Observation"
        [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code"read"
        [:rest 0 :resource 0 :searchParam (search-param "value-quantity") :type]
        := #fhir/code"quantity"
        [:rest 0 :resource 0 :searchParam (search-param "value-quantity") :documentation]
        := #fhir/markdown"Decimal values are truncated at two digits after the decimal point."))

    (testing "one operation"
      (given
        (-> @((capabilities/capabilities-handler
                {:version "version-131640"
                 :structure-definitions
                 [{:kind "resource" :name "Measure"}]
                 :search-param-registry search-param-registry
                 :resource-patterns
                 [#:blaze.rest-api.resource-pattern
                         {:type "Measure"
                          :interactions
                          {:read
                           #:blaze.rest-api.interaction
                                   {:handler (fn [_])}}}]
                 :operations
                 [#:blaze.rest-api.operation
                         {:code "evaluate-measure"
                          :def-uri
                          "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
                          :resource-types ["Measure"]
                          :type-handler (fn [_])
                          :instance-handler (fn [_])}]})
              {})
            :body)
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :resource 0 :type] := #fhir/code"Measure"
        [:rest 0 :resource 0 :operation 0 :name] := "evaluate-measure"
        [:rest 0 :resource 0 :operation 0 :definition] :=
        #fhir/canonical"http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"))))
