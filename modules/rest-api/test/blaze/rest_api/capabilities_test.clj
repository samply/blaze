(ns blaze.rest-api.capabilities-test
  (:require
    [blaze.db.impl.search-param]
    [blaze.fhir.test-util :refer [structure-definition-repo]]
    [blaze.module.test-util :refer [with-system]]
    [blaze.rest-api.capabilities :as capabilities]
    [blaze.rest-api.capabilities-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [reitit.ring]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(def ^:private copyright
  #fhir/markdown"Copyright 2019 - 2023 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.")


(defn- search-param [name]
  (fn [params] (some #(when (= name (:name %)) %) params)))


(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})


(deftest capabilities-handler-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
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
        [:rest 0 :searchParam 0 :name] := "_id"
        [:rest 0 :searchParam 0 :type] := "token"
        [:rest 0 :searchParam 0 :definition] := "http://hl7.org/fhir/SearchParameter/Resource-id"
        [:rest 0 :searchParam 1 :name] := "_lastUpdated"
        [:rest 0 :searchParam 1 :type] := "date"
        [:rest 0 :searchParam 1 :definition] := "http://hl7.org/fhir/SearchParameter/Resource-lastUpdated"
        [:rest 0 :searchParam 2 :name] := "_profile"
        [:rest 0 :searchParam 2 :type] := "uri"
        [:rest 0 :searchParam 2 :definition] := "http://hl7.org/fhir/SearchParameter/Resource-profile"
        [:rest 0 :searchParam 3 :name] := "_security"
        [:rest 0 :searchParam 3 :type] := "token"
        [:rest 0 :searchParam 3 :definition] := "http://hl7.org/fhir/SearchParameter/Resource-security"
        [:rest 0 :searchParam 4 :name] := "_source"
        [:rest 0 :searchParam 4 :type] := "uri"
        [:rest 0 :searchParam 4 :definition] := "http://hl7.org/fhir/SearchParameter/Resource-source"
        [:rest 0 :searchParam 5 :name] := "_tag"
        [:rest 0 :searchParam 5 :type] := "token"
        [:rest 0 :searchParam 5 :definition] := "http://hl7.org/fhir/SearchParameter/Resource-tag"
        [:rest 0 :searchParam 6 :name] := "_list"
        [:rest 0 :searchParam 6 :type] := "special"
        [:rest 0 :searchParam 7 :name] := "_has"
        [:rest 0 :searchParam 7 :type] := "special"
        [:rest 0 :searchParam 8 :name] := "_include"
        [:rest 0 :searchParam 8 :type] := "special"
        [:rest 0 :searchParam 9 :name] := "_revinclude"
        [:rest 0 :searchParam 9 :type] := "special"
        [:rest 0 :searchParam 10 :name] := "_count"
        [:rest 0 :searchParam 10 :type] := "number"
        [:rest 0 :searchParam 10 :documentation] := "The number of resources returned per page"
        [:rest 0 :searchParam 11 :name] := "_elements"
        [:rest 0 :searchParam 11 :type] := "special"
        [:rest 0 :searchParam 12 :name] := "_sort"
        [:rest 0 :searchParam 12 :type] := "special"
        [:rest 0 :searchParam 12 :documentation] := "Only `_lastUpdated` and `-_lastUpdated` is supported at the moment."
        [:rest 0 :searchParam 13 :name] := "_summary"
        [:rest 0 :searchParam 13 :type] := "token"
        [:rest 0 :searchParam 13 :documentation] := "Only `count` is supported at the moment."))

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

    (testing "Patient read interaction"
      (given
        (-> @((capabilities/capabilities-handler
                {:version "version-131640"
                 :structure-definitions
                 [{:url "http://hl7.org/fhir/StructureDefinition/Patient"
                   :name "Patient"
                   :kind "resource"}]
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
        [:rest 0 :resource 0 :profile] := #fhir/canonical"http://hl7.org/fhir/StructureDefinition/Patient"
        [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code"read"
        [:rest 0 :resource 0 :referencePolicy] :? (partial some #{#fhir/code"enforced"})
        [:rest 0 :resource 0 :searchParam 0 :name] := "address-use"
        [:rest 0 :resource 0 :searchParam 0 :type] := #fhir/code"token"
        [:rest 0 :resource 0 :searchParam 1 :name] := "address-country"
        [:rest 0 :resource 0 :searchParam 1 :type] := #fhir/code"string"
        [:rest 0 :resource 0 :searchParam 2 :name] := "death-date"
        [:rest 0 :resource 0 :searchParam 2 :type] := #fhir/code"date"
        [:rest 0 :resource 0 :searchInclude 0] := "Patient:general-practitioner"
        [:rest 0 :resource 0 :searchInclude 1] := "Patient:general-practitioner:Practitioner"
        [:rest 0 :resource 0 :searchInclude 2] := "Patient:general-practitioner:Organization"
        [:rest 0 :resource 0 :searchInclude 3] := "Patient:general-practitioner:PractitionerRole"
        [:rest 0 :resource 0 :searchInclude 4] := "Patient:link"
        [:rest 0 :resource 0 :searchRevInclude 0] := "Account:patient"
        [:rest 0 :resource 0 :searchRevInclude 1] := "Account:subject"
        [:rest 0 :resource 0 :searchRevInclude 2] := "ActivityDefinition:composed-of")

      (testing "with disabled referential integrity check"
        (given
          (-> @((capabilities/capabilities-handler
                  {:version "version-131640"
                   :structure-definitions
                   [{:url "http://hl7.org/fhir/StructureDefinition/Patient"
                     :name "Patient"
                     :kind "resource"}]
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
                 [{:url "http://hl7.org/fhir/StructureDefinition/Observation"
                   :name "Observation"
                   :kind "resource"}]
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
                 [{:url "http://hl7.org/fhir/StructureDefinition/Measure"
                   :name "Measure"
                   :kind "resource"}]
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
        #fhir/canonical"http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure")

      (testing "with documentation"
        (given
          (-> @((capabilities/capabilities-handler
                  {:version "version-131640"
                   :structure-definitions
                   [{:url "http://hl7.org/fhir/StructureDefinition/Measure"
                     :name "Measure"
                     :kind "resource"}]
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
                            :instance-handler (fn [_])
                            :documentation "documentation-161800"}]})
                {})
              :body)
          :fhir/type := :fhir/CapabilityStatement
          [:rest 0 :resource 0 :type] := #fhir/code"Measure"
          [:rest 0 :resource 0 :operation 0 :name] := "evaluate-measure"
          [:rest 0 :resource 0 :operation 0 :definition] :=
          #fhir/canonical"http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
          [:rest 0 :resource 0 :operation 0 :documentation] := #fhir/markdown"documentation-161800")))))
