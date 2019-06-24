(ns blaze.bundle-test
  (:require
    [blaze.bundle :refer [resolve-entry-links]]
    [blaze.datomic.schema :as schema]
    [blaze.structure-definition :refer [read-structure-definitions]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [datomic-tools.schema :as dts]
    [juxt.iota :refer [given]]))


(st/instrument)
(dst/instrument)


(defonce structure-definitions
  (read-structure-definitions "fhir/r4/structure-definitions"))


(defn- connect []
  (d/delete-database "datomic:mem://bundle-test")
  (d/create-database "datomic:mem://bundle-test")
  (let [conn (d/connect "datomic:mem://bundle-test")]
    @(d/transact conn (dts/schema))
    @(d/transact conn (schema/structure-definition-schemas structure-definitions))
    conn))


(defonce db (d/db (connect)))


(deftest resolve-entry-links-test
  (testing "Observation.subject reference"
    (let [entries
          [{"fullUrl" "urn:uuid:9ef14708-5695-4aad-8623-8c8ebd4f48ee"
            "resource"
            {"resourceType" "Observation"
             "id" "0"
             "subject" {"reference" "urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"}}
            "request"
            {"method" "POST"
             "url" "Observation"}}
           {"fullUrl" "urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"
            "resource"
            {"resourceType" "Patient"
             "id" "0"}
            "request"
            {"method" "POST"
             "url" "Patient"}}]]
      (given (resolve-entry-links db entries)
        [0 "resource" "subject" "reference"] := "Patient/0")))

  (testing "Patient.generalPractitioner reference"
    (let [entries
          [{"fullUrl" "urn:uuid:44dded80-aaf1-4988-ace4-5f3a2c9935a7"
            "resource"
            {"resourceType" "Organization"
             "id" "0"}
            "request"
            {"method" "POST"
             "url" "Organization"}}
           {"fullUrl" "urn:uuid:61f73804-78da-4865-8c28-73bdf6f05a2e"
            "resource"
            {"resourceType" "Patient"
             "id" "0"
             "generalPractitioner"
             [{"reference" "urn:uuid:44dded80-aaf1-4988-ace4-5f3a2c9935a7"}]}
            "request"
            {"method" "POST"
             "url" "Patient"}}]]
      (given (resolve-entry-links db entries)
        [1 "resource" "generalPractitioner" 0 "reference"] := "Organization/0")))

  (testing "Claim.diagnosis.diagnosisReference reference"
    (let [entries
          [{"fullUrl" "urn:uuid:69857788-8691-45b9-bc97-654fb93ba615"
            "resource"
            {"resourceType" "Condition"
             "id" "0"}
            "request"
            {"method" "POST"
             "url" "Condition"}}
           {"fullUrl" "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
            "resource"
            {"resourceType" "Claim"
             "id" "0"
             "diagnosis"
             [{"diagnosisReference"
               {"reference" "urn:uuid:69857788-8691-45b9-bc97-654fb93ba615"}}]}
            "request"
            {"method" "POST"
             "url" "Claim"}}]]
      (given (resolve-entry-links db entries)
        [1 "resource" "diagnosis" 0 "diagnosisReference" "reference"] := "Condition/0"))))
