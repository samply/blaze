(ns blaze.rest-api.capabilities-handler-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.impl.search-param]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.middleware.fhir.db-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.capabilities-handler]
   [blaze.rest-api.header-spec]
   [blaze.rest-api.spec]
   [blaze.spec]
   [blaze.terminology-service :as-alias ts]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [reitit.ring]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private minimal-config
  (assoc
   mem-node-config
   ::rest-api/capabilities-handler
   {:version "version-131640"
    :release-date (system/parse-date-time "2024-01-07")
    :structure-definition-repo structure-definition-repo
    :search-param-registry (ig/ref :blaze.db/search-param-registry)}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {::rest-api/capabilities-handler nil}
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::rest-api/capabilities-handler {}}
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :version))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :release-date))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))))

  (testing "invalid version"
    (given-failed-system (assoc-in minimal-config [::rest-api/capabilities-handler :version] ::invalid)
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/version]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid release-date"
    (given-failed-system (assoc-in minimal-config [::rest-api/capabilities-handler :release-date] ::invalid)
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/release-date]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid structure-definition-repo"
    (given-failed-system (assoc-in minimal-config [::rest-api/capabilities-handler :structure-definition-repo] ::invalid)
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/structure-definition-repo]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid search-param-registry"
    (given-failed-system (assoc-in minimal-config [::rest-api/capabilities-handler :search-param-registry] ::invalid)
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/search-param-registry]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid terminology-service"
    (given-failed-system (assoc-in minimal-config [::rest-api/capabilities-handler :terminology-service] ::invalid)
      :key := ::rest-api/capabilities-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/terminology-service]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def ^:private copyright
  #fhir/markdown "Copyright 2019 - 2025 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.")

(defmacro with-handler [[handler-binding config] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# ::rest-api/capabilities-handler} ~config]
       ~txs
       (let [~handler-binding (-> handler# (wrap-db node# 100))]
         ~@body))))

(deftest minimal-config-test
  (with-handler [handler minimal-config]
    (let [{:keys [status headers body]}
          @(handler {:blaze/base-url "base-url-131713"})]

      (is (= 200 status))

      (testing "ETag header"
        (is (= "W/\"707af296\"" (get headers "ETag"))))

      (given body
        :fhir/type := :fhir/CapabilityStatement
        :status := #fhir/code "active"
        :experimental := #fhir/boolean false
        :date := #fhir/dateTime #system/date-time "2024-01-07"
        :publisher := #fhir/string "The Samply Community"
        :copyright := copyright
        :kind := #fhir/code "instance"
        [:software :name] := #fhir/string "Blaze"
        [:software :version] := #fhir/string "version-131640"
        [:implementation :url] := #fhir/url "base-url-131713"
        :fhirVersion := #fhir/code "4.0.1"
        :format := [#fhir/code "application/fhir+json"
                    #fhir/code "application/fhir+xml"]
        [:rest 0 :searchParam 0 :name] := #fhir/string "_id"
        [:rest 0 :searchParam 0 :type] := #fhir/code "token"
        [:rest 0 :searchParam 0 :definition] := #fhir/canonical "http://hl7.org/fhir/SearchParameter/Resource-id"
        [:rest 0 :searchParam 1 :name] := #fhir/string "_lastUpdated"
        [:rest 0 :searchParam 1 :type] := #fhir/code "date"
        [:rest 0 :searchParam 1 :definition] := #fhir/canonical "http://hl7.org/fhir/SearchParameter/Resource-lastUpdated"
        [:rest 0 :searchParam 2 :name] := #fhir/string "_profile"
        [:rest 0 :searchParam 2 :type] := #fhir/code "uri"
        [:rest 0 :searchParam 2 :definition] := #fhir/canonical "http://hl7.org/fhir/SearchParameter/Resource-profile"
        [:rest 0 :searchParam 3 :name] := #fhir/string "_security"
        [:rest 0 :searchParam 3 :type] := #fhir/code "token"
        [:rest 0 :searchParam 3 :definition] := #fhir/canonical "http://hl7.org/fhir/SearchParameter/Resource-security"
        [:rest 0 :searchParam 4 :name] := #fhir/string "_source"
        [:rest 0 :searchParam 4 :type] := #fhir/code "uri"
        [:rest 0 :searchParam 4 :definition] := #fhir/canonical "http://hl7.org/fhir/SearchParameter/Resource-source"
        [:rest 0 :searchParam 5 :name] := #fhir/string "_tag"
        [:rest 0 :searchParam 5 :type] := #fhir/code "token"
        [:rest 0 :searchParam 5 :definition] := #fhir/canonical "http://hl7.org/fhir/SearchParameter/Resource-tag"
        [:rest 0 :searchParam 6 :name] := #fhir/string "_list"
        [:rest 0 :searchParam 6 :type] := #fhir/code "special"
        [:rest 0 :searchParam 7 :name] := #fhir/string "_has"
        [:rest 0 :searchParam 7 :type] := #fhir/code "special"
        [:rest 0 :searchParam 8 :name] := #fhir/string "_include"
        [:rest 0 :searchParam 8 :type] := #fhir/code "special"
        [:rest 0 :searchParam 9 :name] := #fhir/string "_revinclude"
        [:rest 0 :searchParam 9 :type] := #fhir/code "special"
        [:rest 0 :searchParam 10 :name] := #fhir/string "_count"
        [:rest 0 :searchParam 10 :type] := #fhir/code "number"
        [:rest 0 :searchParam 10 :documentation] := #fhir/markdown "The number of resources returned per page"
        [:rest 0 :searchParam 11 :name] := #fhir/string "_elements"
        [:rest 0 :searchParam 11 :type] := #fhir/code "special"
        [:rest 0 :searchParam 12 :name] := #fhir/string "_sort"
        [:rest 0 :searchParam 12 :type] := #fhir/code "special"
        [:rest 0 :searchParam 12 :documentation] := #fhir/markdown "Only `_id`, `_lastUpdated` and `-_lastUpdated` are supported at the moment."
        [:rest 0 :searchParam 13 :name] := #fhir/string "_summary"
        [:rest 0 :searchParam 13 :type] := #fhir/code "token"
        [:rest 0 :searchParam 13 :documentation] := #fhir/markdown "Only `count` is supported at the moment."
        [:rest 0 :compartment] := [#fhir/canonical "http://hl7.org/fhir/CompartmentDefinition/patient"]))

    (testing "filtering by _elements"
      (satisfies-prop 100
        (prop/for-all [ks (gen/vector (gen/elements [:status :software]) 0 50)]
          (let [{:keys [body]}
                @(handler
                  {:query-params {"_elements" (str/join "," (map name ks))}
                   :blaze/base-url "base-url-131713"})]
            (or (empty? ks)
                (= (set (conj ks :fhir/type)) (set (keys body))))))))

    (testing "cache validation"
      (doseq [if-none-match ["W/\"707af296\"" "W/\"707af296\", \"foo\""]]
        (let [{:keys [status headers]}
              @(handler
                {:headers {"if-none-match" if-none-match}
                 :blaze/base-url "base-url-131713"})]

          (is (= 304 status))

          (testing "ETag header"
            (is (= "W/\"707af296\"" (get headers "ETag"))))))))

  (testing "mode=terminology is ignored"
    (with-handler [handler minimal-config]
      (let [{:keys [status body]}
            @(handler {:blaze/base-url "base-url-131713"
                       :query-params {"mode" "terminology"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/CapabilityStatement)))))

(def ^:private minimal-search-system-config
  (assoc-in minimal-config [::rest-api/capabilities-handler :search-system-handler]
            (fn [_])))

(deftest minimal-search-system-config-test
  (with-handler [handler minimal-search-system-config]
    (let [{:keys [headers body]} @(handler {})]

      (testing "ETag header"
        (is (str/starts-with? (get headers "ETag") "W")))

      (given body
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :interaction 0 :fhir/type] := :fhir.CapabilityStatement.rest/interaction
        [:rest 0 :interaction 0 :code] := #fhir/code "search-system"))))

(def ^:private minimal-history-system-config
  (assoc-in minimal-config [::rest-api/capabilities-handler :history-system-handler]
            (fn [_])))

(deftest minimal-history-system-config-test
  (with-handler [handler minimal-history-system-config]
    (let [{:keys [headers body]} @(handler {})]

      (testing "ETag header"
        (is (str/starts-with? (get headers "ETag") "W")))

      (given body
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :interaction 0 :fhir/type] := :fhir.CapabilityStatement.rest/interaction
        [:rest 0 :interaction 0 :code] := #fhir/code "history-system"))))

(def ^:private patient-read-interaction-config
  (assoc-in minimal-config [::rest-api/capabilities-handler :resource-patterns]
            [#:blaze.rest-api.resource-pattern
              {:type "Patient"
               :interactions
               {:read
                #:blaze.rest-api.interaction
                 {:handler (fn [_])}}}]))

(def ^:private parsing-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}))

(defn- patient-profile [structure-definition-repo]
  (->> (sdr/resources structure-definition-repo)
       (some #(when (#{"Patient"} (:name %)) %))
       (j/write-value-as-string)
       (fhir-spec/parse-json parsing-context)))

(deftest patient-read-interaction-test
  (with-handler [handler patient-read-interaction-config]
    (given (:body @(handler {}))
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :fhir/type] := :fhir.CapabilityStatement/rest
      [:rest 0 :resource 0 :fhir/type] := :fhir.CapabilityStatement.rest/resource
      [:rest 0 :resource 0 :type] := #fhir/code "Patient"
      [:rest 0 :resource 0 :profile] := #fhir/canonical "http://hl7.org/fhir/StructureDefinition/Patient"
      [:rest 0 :resource 0 :interaction 0 :fhir/type] := :fhir.CapabilityStatement.rest.resource/interaction
      [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code "read"
      [:rest 0 :resource 0 :conditionalDelete] := #fhir/code "single"
      [:rest 0 :resource 0 :referencePolicy] :? (partial some #{#fhir/code "enforced"})
      [:rest 0 :resource 0 :searchParam 0 :fhir/type] := :fhir.CapabilityStatement.rest.resource/searchParam
      [:rest 0 :resource 0 :searchParam 0 :name] := #fhir/string "address-use"
      [:rest 0 :resource 0 :searchParam 0 :type] := #fhir/code "token"
      [:rest 0 :resource 0 :searchParam 1 :name] := #fhir/string "address-country"
      [:rest 0 :resource 0 :searchParam 1 :type] := #fhir/code "string"
      [:rest 0 :resource 0 :searchParam 2 :name] := #fhir/string "death-date"
      [:rest 0 :resource 0 :searchParam 2 :type] := #fhir/code "date"
      [:rest 0 :resource 0 :searchInclude 0] := #fhir/string "Patient:general-practitioner"
      [:rest 0 :resource 0 :searchInclude 1] := #fhir/string "Patient:general-practitioner:Practitioner"
      [:rest 0 :resource 0 :searchInclude 2] := #fhir/string "Patient:general-practitioner:Organization"
      [:rest 0 :resource 0 :searchInclude 3] := #fhir/string "Patient:general-practitioner:PractitionerRole"
      [:rest 0 :resource 0 :searchInclude 4] := #fhir/string "Patient:link"
      [:rest 0 :resource 0 :searchRevInclude 0] := #fhir/string "Account:patient"
      [:rest 0 :resource 0 :searchRevInclude 1] := #fhir/string "Account:subject"
      [:rest 0 :resource 0 :searchRevInclude 2] := #fhir/string "ActivityDefinition:composed-of"))

  (testing "with disabled referential integrity check"
    (with-handler [handler (assoc-in patient-read-interaction-config
                                     [::rest-api/capabilities-handler
                                      :enforce-referential-integrity]
                                     false)]
      (given (:body @(handler {}))
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :fhir/type] := :fhir.CapabilityStatement/rest
        [:rest 0 :resource 0 :fhir/type] := :fhir.CapabilityStatement.rest/resource
        [:rest 0 :resource 0 :type] := #fhir/code "Patient"
        [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code "read"
        [:rest 0 :resource 0 :referencePolicy] :? (comp not (partial some #{#fhir/code "enforced"})))))

  (testing "with allowed multiple delete"
    (with-handler [handler (assoc-in patient-read-interaction-config
                                     [::rest-api/capabilities-handler
                                      :allow-multiple-delete]
                                     true)]
      (given (:body @(handler {}))
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :fhir/type] := :fhir.CapabilityStatement/rest
        [:rest 0 :resource 0 :fhir/type] := :fhir.CapabilityStatement.rest/resource
        [:rest 0 :resource 0 :type] := #fhir/code "Patient"
        [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code "read"
        [:rest 0 :resource 0 :conditionalDelete] := #fhir/code "multiple")))

  (testing "with custom profiles"
    (with-handler [handler patient-read-interaction-config]
      [[[:create (patient-profile structure-definition-repo)]
        [:create {:fhir/type :fhir/StructureDefinition :id "id-085034"
                  :url #fhir/uri "url-084829"
                  :type #fhir/uri "Patient"
                  :derivation #fhir/code "constraint"}]]]

      (given (:body @(handler {}))
        :fhir/type := :fhir/CapabilityStatement
        [:rest 0 :fhir/type] := :fhir.CapabilityStatement/rest
        [:rest 0 :resource 0 :fhir/type] := :fhir.CapabilityStatement.rest/resource
        [:rest 0 :resource 0 :type] := #fhir/code "Patient"
        [:rest 0 :resource 0 :profile] := #fhir/canonical "http://hl7.org/fhir/StructureDefinition/Patient"
        [:rest 0 :resource 0 :supportedProfile count] := 1
        [:rest 0 :resource 0 :supportedProfile 0] := #fhir/canonical "url-084829")

      (testing "filtering by _elements=software doesn't load supported profiles"
        (with-redefs [d/type-query (fn [_ _ _] (throw (Exception.)))]
          (given (:body @(handler {:query-params {"_elements" "software"}}))
            :fhir/type := :fhir/CapabilityStatement
            [:software :name] := #fhir/string "Blaze")))

      (testing "filtering by _elements=software,rest loads supported profiles"
        (given (:body @(handler {:query-params {"_elements" "software,rest"}}))
          :fhir/type := :fhir/CapabilityStatement
          [:rest 0 :resource 0 :supportedProfile count] := 1)))

    (testing "profile with version"
      (with-handler [handler patient-read-interaction-config]
        [[[:create {:fhir/type :fhir/StructureDefinition :id "id-085034"
                    :url #fhir/uri "url-084829"
                    :version #fhir/string "version-093738"
                    :type #fhir/uri "Patient"
                    :derivation #fhir/code "constraint"}]]]

        (given (:body @(handler {}))
          :fhir/type := :fhir/CapabilityStatement
          [:rest 0 :resource 0 :supportedProfile count] := 1
          [:rest 0 :resource 0 :supportedProfile 0] := #fhir/canonical "url-084829|version-093738")))))

(def ^:private observation-read-interaction-config
  (assoc-in minimal-config [::rest-api/capabilities-handler :resource-patterns]
            [#:blaze.rest-api.resource-pattern
              {:type "Observation"
               :interactions
               {:read
                #:blaze.rest-api.interaction
                 {:handler (fn [_])}}}]))

(defn- search-param [name]
  (fn [params] (some #(when (= name (-> % :name :value)) %) params)))

(deftest observation-read-interaction-test
  (with-handler [handler observation-read-interaction-config]
    (given (:body @(handler {}))
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :fhir/type] := :fhir.CapabilityStatement/rest
      [:rest 0 :resource 0 :fhir/type] := :fhir.CapabilityStatement.rest/resource
      [:rest 0 :resource 0 :type] := #fhir/code "Observation"
      [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code "read"
      [:rest 0 :resource 0 :versioning] := #fhir/code "versioned-update"
      [:rest 0 :resource 0 :readHistory] := #fhir/boolean true
      [:rest 0 :resource 0 :updateCreate] := #fhir/boolean true
      [:rest 0 :resource 0 :conditionalCreate] := #fhir/boolean true
      [:rest 0 :resource 0 :conditionalRead] := #fhir/code "not-supported"
      [:rest 0 :resource 0 :conditionalUpdate] := #fhir/boolean false
      [:rest 0 :resource 0 :conditionalDelete] := #fhir/code "single"
      [:rest 0 :resource 0 :referencePolicy] := [#fhir/code "literal" #fhir/code "local" #fhir/code "enforced"]
      [:rest 0 :resource 0 :searchParam (search-param "value-quantity") :type]
      := #fhir/code "quantity"
      [:rest 0 :resource 0 :searchParam (search-param "value-quantity") :documentation]
      := #fhir/markdown "Decimal values are truncated at two digits after the decimal point.")))

(def ^:private one-type-operation-config
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

(deftest one-type-operation-test
  (with-handler [handler one-type-operation-config]
    (given (:body @(handler {}))
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :fhir/type] := :fhir.CapabilityStatement/rest
      [:rest 0 :resource 0 :fhir/type] := :fhir.CapabilityStatement.rest/resource
      [:rest 0 :resource 0 :type] := #fhir/code "Measure"
      [:rest 0 :resource 0 :operation 0 :fhir/type] := :fhir.CapabilityStatement.rest.resource/operation
      [:rest 0 :resource 0 :operation 0 :name] := #fhir/string "evaluate-measure"
      [:rest 0 :resource 0 :operation 0 :definition] :=
      #fhir/canonical "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure")))

(def ^:private one-type-operation-documentation-config
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

(deftest one-type-operation-documentation-test
  (with-handler [handler one-type-operation-documentation-config]
    (given (:body @(handler {}))
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :fhir/type] := :fhir.CapabilityStatement/rest
      [:rest 0 :resource 0 :fhir/type] := :fhir.CapabilityStatement.rest/resource
      [:rest 0 :resource 0 :type] := #fhir/code "Measure"
      [:rest 0 :resource 0 :operation 0 :fhir/type] := :fhir.CapabilityStatement.rest.resource/operation
      [:rest 0 :resource 0 :operation 0 :name] := #fhir/string "evaluate-measure"
      [:rest 0 :resource 0 :operation 0 :definition] :=
      #fhir/canonical "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
      [:rest 0 :resource 0 :operation 0 :documentation] := #fhir/markdown "documentation-161800")))

(def ^:private one-system-operation-config
  (update minimal-config ::rest-api/capabilities-handler assoc
          :operations
          [#:blaze.rest-api.operation
            {:code "totals"
             :def-uri "https://samply.github.io/blaze/fhir/OperationDefinition/totals"
             :affects-state false
             :resource-types ["Resource"]
             :system-handler (fn [_])}
           #:blaze.rest-api.operation
            {:code "evaluate-measure"
             :def-uri
             "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
             :resource-types ["Measure"]
             :type-handler (fn [_])
             :instance-handler (fn [_])
             :documentation "documentation-161800"}]))

(deftest one-system-operation-test
  (with-handler [handler one-system-operation-config]
    (given (:body @(handler {}))
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :operation count] := 1
      [:rest 0 :operation 0 :fhir/type] := :fhir.CapabilityStatement.rest/operation
      [:rest 0 :operation 0 :name] := #fhir/string "totals"
      [:rest 0 :operation 0 :definition] :=
      #fhir/canonical "https://samply.github.io/blaze/fhir/OperationDefinition/totals")))

(def ^:private one-system-operation-documentation-config
  (update minimal-config ::rest-api/capabilities-handler assoc
          :operations
          [#:blaze.rest-api.operation
            {:code "totals"
             :def-uri "https://samply.github.io/blaze/fhir/OperationDefinition/totals"
             :affects-state false
             :resource-types ["Resource"]
             :system-handler (fn [_])
             :documentation "documentation-141700"}
           #:blaze.rest-api.operation
            {:code "evaluate-measure"
             :def-uri
             "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
             :resource-types ["Measure"]
             :type-handler (fn [_])
             :instance-handler (fn [_])
             :documentation "documentation-161800"}]))

(deftest one-system-operation-documentation-test
  (with-handler [handler one-system-operation-documentation-config]
    (given (:body @(handler {}))
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :operation count] := 1
      [:rest 0 :operation 0 :name] := #fhir/string "totals"
      [:rest 0 :operation 0 :definition] :=
      #fhir/canonical "https://samply.github.io/blaze/fhir/OperationDefinition/totals"
      [:rest 0 :operation 0 :documentation] := #fhir/markdown "documentation-141700")))

(def ^:private terminology-service-config
  (assoc-in
   minimal-config
   [::rest-api/capabilities-handler :terminology-service]
   (ig/ref ::ts/local)))

(deftest terminology-test
  (testing "with no code system"
    (with-handler [handler terminology-service-config]
      (given (:body @(handler {:blaze/base-url "base-url-131713"
                               :query-params {"mode" "terminology"}}))
        :fhir/type := :fhir/TerminologyCapabilities
        [:meta :profile] := [#fhir/canonical "http://hl7.org/fhir/StructureDefinition/TerminologyCapabilities"]
        :status := #fhir/code "active"
        :experimental := #fhir/boolean false
        :date := #fhir/dateTime #system/date-time "2024-01-07"
        :publisher := #fhir/string "The Samply Community"
        :copyright := copyright
        :kind := #fhir/code "instance"
        [:software :fhir/type] := :fhir.TerminologyCapabilities/software
        [:software :name] := #fhir/string "Blaze"
        [:software :version] := #fhir/string "version-131640"
        [:implementation :fhir/type] := :fhir.TerminologyCapabilities/implementation
        [:implementation :url] := #fhir/url "base-url-131713"
        [:codeSystem count] := 0
        [:validateCode :fhir/type] := :fhir.TerminologyCapabilities/validateCode
        [:validateCode :translations] := #fhir/boolean false)))

  (testing "with one code system"
    (with-handler [handler terminology-service-config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-192435"
               :version #fhir/string "version-121451"
               :status #fhir/code "active"
               :content #fhir/code "complete"}]]]

      (given (:body @(handler {:blaze/base-url "base-url-131713"
                               :query-params {"mode" "terminology"}}))
        :fhir/type := :fhir/TerminologyCapabilities
        [:codeSystem count] := 1
        [:codeSystem 0 :fhir/type] := :fhir.TerminologyCapabilities/codeSystem
        [:codeSystem 0 :uri] := #fhir/canonical "system-192435"
        [:codeSystem 0 :version count] := 1
        [:codeSystem 0 :version 0 :fhir/type] := :fhir.TerminologyCapabilities.codeSystem/version
        [:codeSystem 0 :version 0 :code] := #fhir/string "version-121451"
        [:codeSystem 0 :version 0 :isDefault] := #fhir/boolean true))))
