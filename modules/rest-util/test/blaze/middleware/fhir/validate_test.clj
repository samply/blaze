(ns blaze.middleware.fhir.validate-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-spec]
   [blaze.db.api-stub :as api-stub :refer [root-system with-system-data]]
   [blaze.handler.fhir.util-spec]
   [blaze.middleware.fhir.db-spec]
   [blaze.middleware.fhir.validate :refer [wrap-validate]]
   [blaze.middleware.fhir.validate-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc api-stub/mem-node-config
         :blaze/validator {:node (ig/ref :blaze.db/node)
                           :writing-context (:blaze.fhir/writing-context root-system)}))

(deftest wrap-validate-test
  (testing "on empty body"
    (with-system [{:blaze/keys [validator]} config]
      (let [{:keys [status]}
            @((wrap-validate (fn [_] (ac/completed-future (ring/response {}))) validator) {})]

        (testing "shortcuts to handler"
          (is (= 200 status))))))

  (testing "on body without resource type"
    (with-system [{:blaze/keys [validator]} config]
      (let [{:keys [status]}
            @((wrap-validate (fn [_] (ac/completed-future (ring/response {}))) validator)
              {:body {:id "0"}})]

        (testing "shortcuts to handler"
          (is (= 200 status))))))

  (testing "without defined profile"
    (with-system [{:blaze/keys [validator]} config]
      (let [{:keys [status]}
            @((wrap-validate (fn [_] (ac/completed-future (ring/response {}))) validator)
              {:body {:fhir/type :fhir/Patient
                      :id "0"}})]

        (testing "shortcuts to handler"
          (is (= 200 status))))))

  (testing "on non-matching single profile"
    (doseq [tx-data [[]
                     [[[:put {:fhir/type :fhir/StructureDefinition :id "0"
                              :url #fhir/uri "url-110950"
                              :type #fhir/uri "Patient"}]]]
                     [[[:put {:fhir/type :fhir/StructureDefinition :id "0"
                              :url #fhir/uri "url-114730"
                              :type #fhir/uri "Observation"}]]]]]
      (with-system-data [{:blaze/keys [validator]} config]
        tx-data

        (let [{:keys [status body]}
              @((wrap-validate (fn [_] :foo) validator)
                {:body {:fhir/type :fhir/Patient :id "0"
                        :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"processing"
              [:issue 0 :diagnostics] := "Profile reference 'http://example.org/url-114730' has not been checked because it could not be found"))))))

  (testing "on non-matching multiple profiles"
    (with-system-data [{:blaze/keys [validator]} config]
      [[[:put {:fhir/type :fhir/StructureDefinition :id "0"
               :url #fhir/uri "http://example.org/url-110950"
               :type #fhir/uri "Patient"
               :baseDefinition #fhir/canonical "http://hl7.org/fhir/StructureDefinition/Patient"
               :derivation #fhir/code "constraint"}]]]

      (let [{:keys [status body]}
            @((wrap-validate (fn [_] :foo) validator)
              {:body {:fhir/type :fhir/Patient :id "0"
                      :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-110950"
                                                 #fhir/canonical "http://example.org/url-121830"]}}})]

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"processing"
            [:issue 0 :diagnostics] := "Profile reference 'http://example.org/url-121830' has not been checked because it could not be found")))))

  (testing "on matching single profile"
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

      (testing "invalid patient"
        (let [{:keys [status body]}
              @((wrap-validate (fn [_] (ac/completed-future (ring/response {}))) validator)
                {:body {:fhir/type :fhir/Patient :id "0"
                        :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"processing"
              [:issue 0 :diagnostics] := "Patient.active: minimum required = 1, but only found 0 (from http://example.org/url-114730)"))))

      (testing "valid patient"
        (let [{:keys [status]}
              @((wrap-validate (fn [_] (ac/completed-future (ring/response {}))) validator)
                {:body {:fhir/type :fhir/Patient :id "0"
                        :meta #fhir/Meta{:profile [#fhir/canonical "http://example.org/url-114730"]}
                        :active #fhir/boolean true}})]

          (testing "continues to handler"
            (is (= 200 status))))))))
