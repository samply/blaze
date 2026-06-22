(ns blaze.fhir.structure-definition-repo-test
  (:require
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defonce structure-definition-repo
  (ig/init-key :blaze.fhir/structure-definition-repo {}))

(deftest code-expressions-test
  (let [exprs (sdr/code-expressions structure-definition-repo)]
    (is (set? exprs))
    (is (contains? exprs "Observation.status"))
    (is (contains? exprs "Patient.language"))
    (is (contains? exprs "ActivityDefinition.kind"))
    (testing "does not include complex-type nested code paths"
      (is (not (contains? exprs "Patient.address.use")))
      (is (not (contains? exprs "Coding.code")))
      (is (not (contains? exprs "Quantity.code")))
      (is (not (contains? exprs "Address.use")))
      (is (not (contains? exprs "Observation.component.code"))))
    (testing "does not include abstract base resource paths"
      (is (not (contains? exprs "Resource.language"))))))

(deftest canonical-expressions-test
  (let [exprs (sdr/canonical-expressions structure-definition-repo)]
    (is (set? exprs))
    (is (contains? exprs "Resource.meta.profile"))
    (testing "does not include non-canonical paths"
      (is (not (contains? exprs "CapabilityStatement.url")))
      (is (not (contains? exprs "Subscription.channel.endpoint"))))))
