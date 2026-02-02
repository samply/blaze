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
  (testing "code-expressions"
    (let [exprs (sdr/code-expressions structure-definition-repo)]
      (is (set? exprs))
      (is (contains? exprs "Observation.status"))
      (is (contains? exprs "Patient.language"))
      (is (contains? exprs "ActivityDefinition.kind")))))
