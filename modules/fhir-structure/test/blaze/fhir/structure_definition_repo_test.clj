(ns blaze.fhir.structure-definition-repo-test
  (:require
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.fhir.structure-definition-repo-spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defonce structure-definition-repo
  (ig/init-key :blaze.fhir/structure-definition-repo {}))

(deftest expression-types-test
  (let [types (sdr/expression-types structure-definition-repo)]
    (is (map? types))
    (testing "code paths are categorized as :code"
      (is (= :code (types "Observation.status")))
      (is (= :code (types "Patient.language")))
      (is (= :code (types "ActivityDefinition.kind"))))
    (testing "direct canonical paths are categorized as :canonical"
      (is (= :canonical (types "ActivityDefinition.library"))))
    (testing "canonical paths nested through complex types are categorized as :canonical"
      (is (= :canonical (types "Resource.meta.profile"))))
    (testing "canonical-URL paths are categorized as :canonical-url"
      (is (= :canonical-url (types "CapabilityStatement.url")))
      (is (= :canonical-url (types "ValueSet.url")))
      (is (= :canonical-url (types "StructureDefinition.url")))
      (is (= :canonical-url (types "MessageDefinition.url"))))
    (testing "non-canonical .url paths are not categorized"
      (is (nil? (types "Device.url")))
      (is (nil? (types "DeviceDefinition.url")))
      (is (nil? (types "Contract.url"))))
    (testing "non-canonical uri paths are not categorized"
      (is (nil? (types "AuditEvent.agent.policy"))))
    (testing "complex-type nested code paths are not categorized"
      (is (nil? (types "Patient.address.use")))
      (is (nil? (types "Coding.code")))
      (is (nil? (types "Quantity.code")))
      (is (nil? (types "Address.use")))
      (is (nil? (types "Observation.component.code"))))
    (testing "does not include abstract base resource paths"
      (is (nil? (types "Resource.language"))))
    (testing ":ret spec"
      (let [ret-spec (:ret (s/get-spec `sdr/expression-types))]
        (is (s/valid? ret-spec types))
        (is (not (s/valid? ret-spec {"Patient.status" :not-a-valid-kind})))))))
