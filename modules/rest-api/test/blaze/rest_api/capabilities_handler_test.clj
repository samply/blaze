(ns blaze.rest-api.capabilities-handler-test
  (:require
   [blaze.db.impl.search-param]
   [blaze.db.search-param-registry.spec :refer [search-param-registry?]]
   [blaze.fhir.structure-definition-repo.spec :refer [structure-definition-repo?]]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.capabilities-handler]
   [blaze.rest-api.header-spec]
   [blaze.rest-api.spec]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.ring]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::rest-api/capabilities-handler nil})
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::rest-api/capabilities-handler {}})
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :version))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :release-date))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))))

  (testing "invalid version"
    (given-thrown (ig/init {::rest-api/capabilities-handler {:version ::invalid}})
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :release-date))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:cause-data ::s/problems 3 :pred] := `string?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid release-date"
    (given-thrown (ig/init {::rest-api/capabilities-handler {:release-date ::invalid}})
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :version))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:cause-data ::s/problems 3 :pred] := `string?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid structure-definition-repo"
    (given-thrown (ig/init {::rest-api/capabilities-handler {:structure-definition-repo ::invalid}})
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :version))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :release-date))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:cause-data ::s/problems 3 :pred] := `structure-definition-repo?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid search-param-registry"
    (given-thrown (ig/init {::rest-api/capabilities-handler {:search-param-registry ::invalid}})
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :version))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :release-date))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 3 :pred] := `search-param-registry?
      [:cause-data ::s/problems 3 :val] := ::invalid)))

(def ^:private copyright
  #fhir/markdown"Copyright 2019 - 2024 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.")

(defmacro with-handler [[handler-binding config] & body]
  `(with-system [{handler# ::rest-api/capabilities-handler} ~config]
     (let [~handler-binding handler#]
       ~@body)))

(def ^:private minimal-config
  {::rest-api/capabilities-handler
   {:version "version-131640"
    :release-date "2024-01-07"
    :structure-definition-repo structure-definition-repo
    :search-param-registry (ig/ref :blaze.db/search-param-registry)}
   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(deftest minimal-config-test
  (with-handler [handler minimal-config]
    (let [{:keys [status headers body]}
          @(handler {:blaze/base-url "base-url-131713"})]

      (is (= 200 status))

      (testing "ETag header"
        (is (= "W/\"66d24e11\"" (get headers "ETag"))))

      (given body
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
                    #fhir/code"application/fhir+xml"]
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
        [:rest 0 :searchParam 12 :documentation] := "Only `_id`, `_lastUpdated` and `-_lastUpdated` are supported at the moment."
        [:rest 0 :searchParam 13 :name] := "_summary"
        [:rest 0 :searchParam 13 :type] := "token"
        [:rest 0 :searchParam 13 :documentation] := "Only `count` is supported at the moment."
        [:rest 0 :compartment] := ["http://hl7.org/fhir/CompartmentDefinition/patient"]))

    (testing "filtering by _elements"
      (tu/satisfies-prop 100
        (prop/for-all [ks (gen/vector (gen/elements [:status :software]) 0 50)]
          (let [{:keys [body]}
                @(handler
                  {:query-params {"_elements" (str/join "," (map name ks))}
                   :blaze/base-url "base-url-131713"})]
            (or (empty? ks)
                (= (set (conj ks :fhir/type)) (set (keys body))))))))

    (testing "cache validation"
      (doseq [if-none-match ["W/\"66d24e11\"" "W/\"66d24e11\", \"foo\""]]
        (let [{:keys [status headers]}
              @(handler
                {:headers {"if-none-match" if-none-match}
                 :blaze/base-url "base-url-131713"})]

          (is (= 304 status))

          (testing "ETag header"
            (is (= "W/\"66d24e11\"" (get headers "ETag")))))))))

(def ^:private minimal-search-system-config
  (assoc-in minimal-config [::rest-api/capabilities-handler :search-system-handler]
            ::search-system))

(deftest minimal-search-system-config-test
  (with-handler [handler minimal-search-system-config]
    (let [{:keys [headers body]} @(handler {})]

      (testing "ETag header"
        (is (= "W/\"f30d459a\"" (get headers "ETag"))))

      (given body
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :interaction 0 :code] := #fhir/code"search-system"))))

(def ^:private minimal-history-system-config
  (assoc-in minimal-config [::rest-api/capabilities-handler :history-system-handler]
            ::history-system))

(deftest minimal-history-system-config-test
  (with-handler [handler minimal-history-system-config]
    (let [{:keys [headers body]} @(handler {})]

      (testing "ETag header"
        (is (= "W/\"fdf85726\"" (get headers "ETag"))))

      (given body
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :interaction 0 :code] := #fhir/code"history-system"))))

(def ^:private patient-read-interaction-config
  (assoc-in minimal-config [::rest-api/capabilities-handler :resource-patterns]
            [#:blaze.rest-api.resource-pattern
              {:type "Patient"
               :interactions
               {:read
                #:blaze.rest-api.interaction
                 {:handler (fn [_])}}}]))

(deftest patient-read-interaction-test
  (with-handler [handler patient-read-interaction-config]
    (given (:body @(handler {}))
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
      [:rest 0 :resource 0 :searchRevInclude 2] := "ActivityDefinition:composed-of"))

  (testing "with disabled referential integrity check"
    (with-handler [handler (assoc-in patient-read-interaction-config
                                     [::rest-api/capabilities-handler
                                      :enforce-referential-integrity]
                                     false)]
      (given (:body @(handler {}))
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :resource 0 :type] := #fhir/code"Patient"
        [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code"read"
        [:rest 0 :resource 0 :referencePolicy] :? (comp not (partial some #{#fhir/code"enforced"}))))))

(def ^:private observation-read-interaction-config
  (assoc-in minimal-config [::rest-api/capabilities-handler :resource-patterns]
            [#:blaze.rest-api.resource-pattern
              {:type "Observation"
               :interactions
               {:read
                #:blaze.rest-api.interaction
                 {:handler (fn [_])}}}]))

(defn- search-param [name]
  (fn [params] (some #(when (= name (:name %)) %) params)))

(deftest observation-read-interaction-test
  (with-handler [handler observation-read-interaction-config]
    (given (:body @(handler {}))
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :resource 0 :type] := #fhir/code"Observation"
      [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code"read"
      [:rest 0 :resource 0 :searchParam (search-param "value-quantity") :type]
      := #fhir/code"quantity"
      [:rest 0 :resource 0 :searchParam (search-param "value-quantity") :documentation]
      := #fhir/markdown"Decimal values are truncated at two digits after the decimal point.")))

(def ^:private one-operation-config
  (update minimal-config ::rest-api/capabilities-handler assoc
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
             :instance-handler (fn [_])}]))

(deftest one-operation-test
  (with-handler [handler one-operation-config]
    (given (:body @(handler {}))
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :resource 0 :type] := #fhir/code"Measure"
      [:rest 0 :resource 0 :operation 0 :name] := "evaluate-measure"
      [:rest 0 :resource 0 :operation 0 :definition] :=
      #fhir/canonical"http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure")))

(def ^:private one-operation-documentation-config
  (update minimal-config ::rest-api/capabilities-handler assoc
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
             :documentation "documentation-161800"}]))

(deftest one-operation-documentation-test
  (with-handler [handler one-operation-documentation-config]
    (given (:body @(handler {}))
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :resource 0 :type] := #fhir/code"Measure"
      [:rest 0 :resource 0 :operation 0 :name] := "evaluate-measure"
      [:rest 0 :resource 0 :operation 0 :definition] :=
      #fhir/canonical"http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
      [:rest 0 :resource 0 :operation 0 :documentation] := #fhir/markdown"documentation-161800")))
