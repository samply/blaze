(ns blaze.bundle-test
  (:require
    [blaze.bundle :refer [resolve-entry-links tx-data]]
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.terminology-service :as ts]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [juxt.iota :refer [given]]
    [manifold.deferred :as md]))


(st/instrument)
(dst/instrument)


(defonce db (d/db (st/with-instrument-disabled (datomic-test-util/connect))))


(def term-service
  (reify ts/TermService
    (-expand-value-set [_ _]
      (md/error-deferred {::anom/category ::anom/fault}))))


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


(deftest resolve-entry-links-in-contained-resources-test
  (let [entries
        [{"fullUrl" "urn:uuid:48aacf48-ba32-4aa8-ac0d-b095ac54201b"
          "resource"
          {"resourceType" "Patient"
           "id" "0"}
          "request"
          {"method" "POST"
           "url" "Patient"}}
         {"fullUrl" "urn:uuid:d0f40d1f-2f95-4990-a994-8182cfe71bc2"
          "resource"
          {"resourceType" "ExplanationOfBenefit"
           "id" "0"
           "contained"
           [{"resourceType" "ServiceRequest"
             "id" "0"
             "subject"
             {"reference" "urn:uuid:48aacf48-ba32-4aa8-ac0d-b095ac54201b"}}]}
          "request"
          {"method" "POST"
           "url" "ExplanationOfBenefit"}}]]
    (given (resolve-entry-links db entries)
      [1 "resource" "contained" 0 "subject" "reference"] := "Patient/0")))


(deftest tx-data-test
  (testing "Single POST"
    (let [resource {"resourceType" "Patient" "id" "0"}]
      (datomic-test-util/stub-resource-upsert
        db {"Patient" {"0" :part/Patient}} :server-assigned-id #{resource}
        #{[::resource-upsert-tx-data]})

      (is
        (=
          (with-redefs [d/tempid (fn [partition] partition)]
            (tx-data
              db
              [{"request" {"method" "POST"}
                "resource" resource}]))
          [[:fn/increment-system-total 1]
           [:fn/increment-system-version 1]
           [:fn/increment-type-total :Patient 1]
           [:fn/increment-type-version :Patient 1]
           [:db/add "datomic.tx" :tx/resources :part/Patient]
           ::resource-upsert-tx-data]))))


  (testing "Single PUT"
    (testing "with non-existing resource"
      (let [resource {"resourceType" "Patient" "id" "0"}]
        (datomic-test-util/stub-resource-upsert
          db {"Patient" {"0" :part/Patient}} :client-assigned-id #{resource}
          #{[::resource-upsert-tx-data]})

        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (tx-data
                db
                [{"request" {"method" "PUT"}
                  "resource" resource}]))
            [[:fn/increment-system-total 1]
             [:fn/increment-system-version 1]
             [:fn/increment-type-total :Patient 1]
             [:fn/increment-type-version :Patient 1]
             [:db/add "datomic.tx" :tx/resources :part/Patient]
             ::resource-upsert-tx-data]))))

    (testing "with existing resource and no differences"
      (let [[db] (datomic-test-util/with-resource db "Patient" "0")
            resource {"resourceType" "Patient" "id" "0"}]
        (datomic-test-util/stub-resource-upsert
          db {} :client-assigned-id #{resource} #{[]})

        (is
          (empty?
            (tx-data
              db
              [{"request" {"method" "PUT"}
                "resource" resource}])))))

    (testing "with existing resource and novelty"
      (let [[db id] (datomic-test-util/with-resource db "Patient" "0")
            resource {"resourceType" "Patient" "id" "0" "active" true}]
        (datomic-test-util/stub-resource-upsert
          db {} :client-assigned-id #{resource} #{[::resource-upsert-tx-data]})

        (is
          (=
            (tx-data
              db
              [{"request" {"method" "PUT"}
                "resource" resource}])
            [[:fn/increment-system-version 1]
             [:fn/increment-type-version :Patient 1]
             [:db/add "datomic.tx" :tx/resources id]
             ::resource-upsert-tx-data])))))


  (testing "Multiple PUT"
    (testing "with non-existing resource"
      (datomic-test-util/stub-resource-upsert
        db {"Patient" {"0" :part/Patient}
            "Observation" {"1" :part/Observation}}
        :client-assigned-id map?
        #{[::resource-upsert-tx-data]})

      (is
        (=
          (with-redefs [d/tempid (fn [partition] partition)]
            (tx-data
              db
              [{"request" {"method" "PUT"}
                "resource" {"resourceType" "Patient" "id" "0"}}
               {"request" {"method" "PUT"}
                "resource" {"resourceType" "Observation" "id" "1"}}]))
          [[:fn/increment-system-total 2]
           [:fn/increment-system-version 2]
           [:fn/increment-type-total :Patient 1]
           [:fn/increment-type-version :Patient 1]
           [:fn/increment-type-total :Observation 1]
           [:fn/increment-type-version :Observation 1]
           [:db/add "datomic.tx" :tx/resources :part/Patient]
           [:db/add "datomic.tx" :tx/resources :part/Observation]
           ::resource-upsert-tx-data
           ::resource-upsert-tx-data]))))


  (testing "Single DELETE"
    (testing "with non-existing resource"
      (datomic-test-util/stub-resource-deletion
        db "Patient" "0" #{[]})

      (is
        (empty?
          (tx-data
            db
            [{"request" {"method" "DELETE" "url" "Patient/0"}}]))))

    (testing "with existing resource"
      (let [[db id] (datomic-test-util/with-resource db "Patient" "0")]
        (datomic-test-util/stub-resource-deletion
          db "Patient" "0" #{[::resource-deletion-tx-data]})

        (is
          (=
            (tx-data
              db
              [{"request" {"method" "DELETE" "url" "Patient/0"}}])
            [[:fn/increment-system-total -1]
             [:fn/increment-system-version 1]
             [:fn/increment-type-total :Patient -1]
             [:fn/increment-type-version :Patient 1]
             [:db/add "datomic.tx" :tx/resources id]
             ::resource-deletion-tx-data]))))


    (testing "One POST and one DELETE"
      (let [[db id] (datomic-test-util/with-resource db "Observation" "1")
            patient {"resourceType" "Patient" "id" "0"}]
        (datomic-test-util/stub-resource-upsert
          db {"Patient" {"0" :part/Patient}}
          :client-assigned-id #{patient}
          #{[::resource-upsert-tx-data]})
        (datomic-test-util/stub-resource-deletion
          db "Observation" "1" #{[::resource-deletion-tx-data]})

        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (tx-data
                db
                [{"request" {"method" "PUT"}
                  "resource" patient}
                 {"request" {"method" "DELETE" "url" "Observation/1"}}]))
            [[:fn/increment-system-version 2]
             [:fn/increment-type-total :Patient 1]
             [:fn/increment-type-version :Patient 1]
             [:fn/increment-type-total :Observation -1]
             [:fn/increment-type-version :Observation 1]
             [:db/add "datomic.tx" :tx/resources :part/Patient]
             [:db/add "datomic.tx" :tx/resources id]
             ::resource-upsert-tx-data
             ::resource-deletion-tx-data]))))))
