(ns blaze.validator-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.api-stub :as api-stub :refer [root-system with-system-data]]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.test-util :as tu]
   [blaze.validator :as validator]
   [blaze.validator.spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import [okhttp3.mockwebserver Dispatcher MockResponse MockWebServer RecordedRequest]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc api-stub/mem-node-config
         :blaze/validator {:node (ig/ref :blaze.db/node)
                           :writing-context (:blaze.fhir/writing-context root-system)}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/validator nil}
      :key := :blaze/validator
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze/validator {}}
      :key := :blaze/validator
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :writing-context))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [:blaze/validator :node] ::invalid)
      :key := :blaze/validator
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid writing context"
    (given-failed-system (assoc-in config [:blaze/validator :writing-context] ::invalid)
      :key := :blaze/validator
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/writing-context]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid terminology-service-base-url"
    (given-failed-system (assoc-in config [:blaze/validator :terminology-service-base-url] ::invalid)
      :key := :blaze/validator
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.validator/terminology-service-base-url]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "with minimal config"
    (with-system [{:blaze/keys [validator]} config]
      (is (some? validator)))))

(deftest validate-test
  (log/set-min-level! :debug)
  (testing "with existing profile"
    (let [mock-web-server (MockWebServer.)
          mock-base-url (str (.url mock-web-server "fhir"))
          dispatcher (proxy [Dispatcher]
                            []
                       (dispatch [^RecordedRequest request]
                         (.. request getRequestUrl encodedPath)
                         (.. request getRequestUrl encodedQuery)
                         (condp = (.. request getRequestUrl encodedPath)
                           "/fhir/ValueSet"
                           (-> (MockResponse.)
                             (.setBody "{\"resourceType\": \"Bundle\"}")
                             (.setHeader "Content-Type" "application/fhir+json"))

                           "/fhir/metadata"
                           (-> (MockResponse.)
                             (.setBody "{\"resourceType\": \"CapabilityStatement\", \"fhirVersion\": \"4.0.1\"}")
                             (.setHeader "Content-Type" "application/fhir+json"))

                           (-> (MockResponse.)
                             (.setBody "{}")
                             (.setHeader "Content-Type" "application/fhir+json")))))

          config (assoc-in config [:blaze/validator :terminology-service-base-url] mock-base-url)]

      (.setDispatcher mock-web-server dispatcher)

      (with-system-data [{:blaze/keys [validator]} config]
        [[[:put {:fhir/type :fhir/StructureDefinition :id "0"
                 :url #fhir/uri "http://example.org/url-114730"
                 :type #fhir/uri "Patient"
                 :baseDefinition #fhir/canonical "http://hl7.org/fhir/StructureDefinition/Patient"
                 :derivation #fhir/code "constraint"
                 :differential
                 {:fhir/type :fhir.StructureDefinition/differential
                  :element
                  [{:fhir/type :fhir/ElementDefinition
                    :id "Patient.active"
                    :path #fhir/string "Patient.active"
                    :mustSupport #fhir/boolean true
                    :min #fhir/unsignedInt 1}]}}]]]

        (testing "on matching profile for resource type"
          (testing "invalid patient"
            (let [result (validator/validate validator {:fhir/type :fhir/Patient :id "0"
                                                        :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}})]

              (testing "returns error"
                (given result
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code"error"
                  [:issue 0 :code] := #fhir/code"processing"
                  [:issue 0 :diagnostics] := "Patient.active: minimum required = 1, but only found 0 (from http://example.org/url-114730)"
                  [:issue 0 :expression] := ["Patient"])))

            (testing "in bundle"
              (let [result (validator/validate validator {:fhir/type :fhir/Bundle
                                                          :type #fhir/code "transaction"
                                                          :entry
                                                          [{:fhir/type :fhir.Bundle/entry
                                                            :resource
                                                            {:fhir/type :fhir/Patient
                                                             :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}}
                                                            :request
                                                            {:fhir/type :fhir.Bundle.entry/request
                                                             :method #fhir/code "POST"
                                                             :url #fhir/uri "/Patient"}}]})]
                (testing "returns error"
                  (given result
                    :fhir/type := :fhir/OperationOutcome
                    [:issue 0 :severity] := #fhir/code"error"
                    [:issue 0 :code] := #fhir/code"processing"
                    [:issue 0 :diagnostics] := "Patient.active: minimum required = 1, but only found 0 (from http://example.org/url-114730)"))))))

        (testing "valid patient"
          (is (nil? (validator/validate validator {:fhir/type :fhir/Patient :id "0"
                                                   :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}
                                                   :active #fhir/boolean true})))

          (testing "in bundle"
            (is (nil? (validator/validate validator {:fhir/type :fhir/Bundle
                                                     :type #fhir/code "transaction"
                                                     :entry
                                                     [{:fhir/type :fhir.Bundle/entry
                                                       :resource
                                                       {:fhir/type :fhir/Patient
                                                        :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}
                                                        :active #fhir/boolean true}
                                                       :request
                                                       {:fhir/type :fhir.Bundle.entry/request
                                                        :method #fhir/code "POST"
                                                        :url #fhir/uri "/Patient"}}]})))))

        (testing "on non-matching profile of other resource type"
          (let [result (validator/validate validator {:fhir/type :fhir/Observation :id "0"
                                                      :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}})]

            (testing "returns error"
              (given result
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"processing"
                [:issue 0 :diagnostics] := "Specified profile type was 'Patient' in profile 'http://example.org/url-114730', but found type 'Observation'"))))

        (testing "on defined profile of other resource type in db"
          (testing "valid observation"
            (is (nil? (validator/validate validator {:fhir/type :fhir/Observation
                                                     :id "0"
                                                     :status #fhir/code "registered"
                                                     :code #fhir/CodeableConcept{:text #fhir/string "loinc"}}))))

          (testing "invalid observation"
            (let [result (validator/validate validator {:fhir/type :fhir/Observation
                                                        :id "0"
                                                        :status #fhir/code "registered"})]
              (testing "returns error"
                (given result
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code"error"
                  [:issue 0 :code] := #fhir/code"processing"
                  [:issue 0 :diagnostics] := "Observation.code: minimum required = 1, but only found 0 (from http://hl7.org/fhir/StructureDefinition/Observation|4.0.1)"))))

          (testing "invalid observation in bundle"
            (let [result (validator/validate validator {:fhir/type :fhir/Bundle
                                                        :type #fhir/code "transaction"
                                                        :entry
                                                        [{:fhir/type :fhir.Bundle/entry
                                                          :resource
                                                          {:fhir/type :fhir/Observation
                                                           :status #fhir/code "registered"}
                                                          :request
                                                          {:fhir/type :fhir.Bundle.entry/request
                                                           :method #fhir/code "POST"
                                                           :url #fhir/uri "/Observation"}}]})]
              (testing "returns error"
                (given result
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code"error"
                  [:issue 0 :code] := #fhir/code"processing"
                  [:issue 0 :diagnostics] := "Observation.code: minimum required = 1, but only found 0 (from http://hl7.org/fhir/StructureDefinition/Observation|4.0.1)")))))

        (testing "without defined profile"
          (with-system [{:blaze/keys [validator]} config]
            (testing "without meta profile"
              (testing "invalid patient"
                (let [result (validator/validate validator {:fhir/type :fhir/Patient :id "0"
                                                            :communication
                                                            [{:fhir/type :fhir.Patient/communication
                                                              :preferred true}]})]

                  (testing "returns error"
                    (given result
                      :fhir/type := :fhir/OperationOutcome
                      [:issue 0 :severity] := #fhir/code"error"
                      [:issue 0 :code] := #fhir/code"processing"
                      [:issue 0 :diagnostics] := "Patient.communication.language: minimum required = 1, but only found 0 (from http://hl7.org/fhir/StructureDefinition/Patient|4.0.1)"))))

              (testing "valid patient"
                (is (nil? (validator/validate validator {:fhir/type :fhir/Patient :id "0"})))))

            (testing "with meta profile"
              (testing "invalid patient"
                (let [result (validator/validate validator {:fhir/type :fhir/Patient :id "0"
                                                            :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}})]

                  (testing "returns error"
                    (given result
                      :fhir/type := :fhir/OperationOutcome
                      [:issue 0 :severity] := #fhir/code"error"
                      [:issue 0 :code] := #fhir/code"processing"
                      [:issue 0 :diagnostics] := "Profile reference 'http://example.org/url-114730' has not been checked because it could not be found")))

                (testing "in bundle"
                  (let [result (validator/validate validator {:fhir/type :fhir/Bundle
                                                              :type #fhir/code "transaction"
                                                              :entry
                                                              [{:fhir/type :fhir.Bundle/entry
                                                                :resource
                                                                {:fhir/type :fhir/Patient
                                                                 :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}}}]})]

                    (testing "returns error"
                      (given result
                        :fhir/type := :fhir/OperationOutcome
                        [:issue 0 :severity] := #fhir/code"error"
                        [:issue 0 :code] := #fhir/code"processing"
                        [:issue 0 :diagnostics] := "Profile reference 'http://example.org/url-114730' has not been checked because it could not be found"))))))))))))

(deftest invalidate-validator-caches-test
  (testing "on ingesting new StructureDefinition"

    (with-system [{:blaze/keys [validator] :blaze.db/keys [node]} config]
      (testing "with meta profile"
        (testing "valid patient"
          (let [result (validator/validate validator {:fhir/type :fhir/Patient :id "0"
                                                      :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}})]

            (testing "returns error"
              (given result
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"processing"
                [:issue 0 :diagnostics] := "Profile reference 'http://example.org/url-114730' has not been checked because it could not be found")))))

      (testing "on ingesting new profile"
        @(d/transact node [[:put {:fhir/type :fhir/StructureDefinition :id "0"
                                  :url #fhir/uri "http://example.org/url-114730"
                                  :type #fhir/uri "Patient"
                                  :baseDefinition #fhir/canonical "http://hl7.org/fhir/StructureDefinition/Patient"
                                  :derivation #fhir/code "constraint"
                                  :differential
                                  {:fhir/type :fhir.StructureDefinition/differential
                                   :element
                                   [{:fhir/type :fhir/ElementDefinition
                                     :id "Patient.active"
                                     :path #fhir/string "Patient.active"
                                     :mustSupport #fhir/boolean true
                                     :min #fhir/unsignedInt 1}]}}]])

        (testing "with invalid patient"
          (let [result (validator/validate validator {:fhir/type :fhir/Patient :id "0"
                                                      :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}})]

            (testing "returns error"
              (given result
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"processing"
                [:issue 0 :diagnostics] := "Patient.active: minimum required = 1, but only found 0 (from http://example.org/url-114730)"))))

        (testing "with valid patient"
          (is (nil? (validator/validate validator {:fhir/type :fhir/Patient :id "0"
                                                   :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}
                                                   :active #fhir/boolean true}))))))))