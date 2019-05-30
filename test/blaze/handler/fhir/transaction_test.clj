(ns blaze.handler.fhir.transaction-test
  "Specifications relevant for the FHIR batch/transaction interaction:

  https://www.hl7.org/fhir/http.html#transaction
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.bundle :as bundle]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.test-util :as test-util]
    [blaze.handler.fhir.transaction :refer [handler-intern]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [datomic-spec.test :as dst])
  (:import
    [java.time Instant]))


(st/instrument)
(dst/instrument)


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (test-util/stub-db ::conn ::db-before)
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(def base-uri "http://localhost:8080")


(defn- stub-tx-instant [transaction instant]
  (st/instrument
    [`util/tx-instant]
    {:spec
     {`util/tx-instant
      (s/fspec
        :args (s/cat :transaction #{transaction})
        :ret #{instant})}
     :stub
     #{`util/tx-instant}}))


(defn- stub-tx-data [db entries-spec result]
  (st/instrument
    [`bundle/tx-data]
    {:spec
     {`bundle/tx-data
      (s/fspec
        :args (s/cat :db #{db} :entries entries-spec)
        :ret #{result})}
     :stub
     #{`bundle/tx-data}}))


(defn- stub-squuid [id]
  (st/instrument
    [`d/squuid]
    {:spec
     {`d/squuid
      (s/fspec
        :args (s/cat)
        :ret #{id})}
     :stub
     #{`d/squuid}}))


(deftest handler-test
  (testing "Returns Error on unknown type"
    (test-util/stub-cached-entity ::db-before #{:Foo} nil?)

    (let [{:keys [status body]}
          @((handler-intern base-uri ::conn)
             {:body
              {"resourceType" "Bundle"
               "id" "01a674d5-2a05-43a7-9ed4-b4bd7c676621"
               "type" "transaction"
               "entry"
               [{"request"
                 {"method" "PUT"
                  "url" "Foo/0"}}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "value" (-> body :issue first :code)))

      (is (= "Unknown type `Foo`." (-> body :issue first :diagnostics)))))


  (testing "Returns Error on type mismatch of a update"
    (test-util/stub-cached-entity ::db-before #{:Patient} some?)

    (let [{:keys [status body]}
          @((handler-intern base-uri ::conn)
             {:body
              {"resourceType" "Bundle"
               "id" "01a674d5-2a05-43a7-9ed4-b4bd7c676621"
               "type" "transaction"
               "entry"
               [{"resource"
                 {"resourceType" "Observation"}
                 "request"
                 {"method" "PUT"
                  "url" "Patient/0"}}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "invariant" (-> body :issue first :code)))

      (is (= "http://terminology.hl7.org/CodeSystem/operation-outcome"
             (-> body :issue first :details :coding first :system)))

      (is (= "MSG_RESOURCE_TYPE_MISMATCH"
             (-> body :issue first :details :coding first :code)))))


  (testing "Returns Error on ID mismatch of a update"
    (test-util/stub-cached-entity ::db-before #{:Patient} some?)

    (let [{:keys [status body]}
          @((handler-intern base-uri ::conn)
             {:body
              {"resourceType" "Bundle"
               "id" "01a674d5-2a05-43a7-9ed4-b4bd7c676621"
               "type" "transaction"
               "entry"
               [{"resource"
                 {"resourceType" "Patient"
                  "id" "1"}
                 "request"
                 {"method" "PUT"
                  "url" "Patient/0"}}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "invariant" (-> body :issue first :code)))

      (is (= "http://terminology.hl7.org/CodeSystem/operation-outcome"
             (-> body :issue first :details :coding first :system)))

      (is (= "MSG_RESOURCE_ID_MISMATCH"
             (-> body :issue first :details :coding first :code)))))


  (testing "On newly created resource of a update in batch"
    (let [resource
          {"resourceType" "Patient"
           "id" "0"}]

      (test-util/stub-cached-entity ::db-before #{:Patient} some?)
      (test-util/stub-resource ::db-before #{"Patient"} #{"0"} nil?)
      (test-util/stub-upsert-resource ::conn ::db-before 1 resource {:db-after ::db-after})
      (test-util/stub-basis-transaction ::db-after ::transaction)
      (stub-tx-instant ::transaction (Instant/ofEpochMilli 0))
      (test-util/stub-basis-t ::db-after "42")

      (let [{:keys [status body]}
            @((handler-intern base-uri ::conn)
               {:body
                {"resourceType" "Bundle"
                 "id" "01a674d5-2a05-43a7-9ed4-b4bd7c676621"
                 "type" "batch"
                 "entry"
                 [{"resource"
                   resource
                   "request"
                   {"method" "PUT"
                    "url" "Patient/0"}}]}})]

        (is (= 200 status))

        (is (= "Bundle" (:resourceType body)))

        (is (= "batch-response" (:type body)))

        (is (= "201" (-> body :entry first :response :status)))

        (is (= "http://localhost:8080/fhir/Patient/0/_history/42"
               (-> body :entry first :response :location)))

        (is (= "W/\"42\"" (-> body :entry first :response :etag)))

        (is (= "1970-01-01T00:00:00Z"
               (-> body :entry first :response :lastModified))))))


  (testing "On newly created resource of a update in transaction"
    (let [resource
          {"resourceType" "Patient"
           "id" "0"}
          entries
          [{"resource"
            resource
            "request"
            {"method" "PUT"
             "url" "Patient/0"}}]]

      (test-util/stub-cached-entity ::db-before #{:Patient} some?)
      (test-util/stub-resource ::db-before #{"Patient"} #{"0"} nil?)
      (stub-tx-data ::db-before #{entries} ::tx-data)
      (test-util/stub-transact-async ::conn ::tx-data {:db-after ::db-after})
      (test-util/stub-basis-transaction ::db-after ::transaction)
      (stub-tx-instant ::transaction (Instant/ofEpochMilli 0))
      (test-util/stub-basis-t ::db-after "42")

      (let [{:keys [status body]}
            @((handler-intern base-uri ::conn)
               {:body
                {"resourceType" "Bundle"
                 "id" "01a674d5-2a05-43a7-9ed4-b4bd7c676621"
                 "type" "transaction"
                 "entry" entries}})]

        (is (= 200 status))

        (is (= "Bundle" (:resourceType body)))

        (is (= "transaction-response" (:type body)))

        (is (= "201" (-> body :entry first :response :status)))

        (is (= "http://localhost:8080/fhir/Patient/0/_history/42"
               (-> body :entry first :response :location)))

        (is (= "W/\"42\"" (-> body :entry first :response :etag)))

        (is (= "1970-01-01T00:00:00Z"
               (-> body :entry first :response :lastModified))))))

  (testing "On updated resource in batch"
    (let [resource
          {"resourceType" "Patient"
           "id" "0"}]

      (test-util/stub-cached-entity ::db-before #{:Patient} some?)
      (test-util/stub-resource ::db-before #{"Patient"} #{"0"} some?)
      (test-util/stub-upsert-resource ::conn ::db-before 1 resource {:db-after ::db-after})
      (test-util/stub-basis-transaction ::db-after ::transaction)
      (stub-tx-instant ::transaction (Instant/ofEpochMilli 0))
      (test-util/stub-basis-t ::db-after "42")

      (let [{:keys [status body]}
            @((handler-intern base-uri ::conn)
               {:body
                {"resourceType" "Bundle"
                 "id" "01a674d5-2a05-43a7-9ed4-b4bd7c676621"
                 "type" "batch"
                 "entry"
                 [{"resource"
                   resource
                   "request"
                   {"method" "PUT"
                    "url" "Patient/0"}}]}})]

        (is (= 200 status))

        (is (= "Bundle" (:resourceType body)))

        (is (= "batch-response" (:type body)))

        (is (= "200" (-> body :entry first :response :status)))

        (is (= "W/\"42\"" (-> body :entry first :response :etag)))

        (is (= "1970-01-01T00:00:00Z"
               (-> body :entry first :response :lastModified))))))


  (testing "On updated resource in transaction"
    (let [resource
          {"resourceType" "Patient"
           "id" "0"}
          entries
          [{"resource"
            resource
            "request"
            {"method" "PUT"
             "url" "Patient/0"}
            :blaze/old-resource ::old-patient}]]

      (test-util/stub-cached-entity ::db-before #{:Patient} some?)
      (test-util/stub-resource ::db-before #{"Patient"} #{"0"} #{::old-patient})
      (stub-tx-data ::db-before #{entries} ::tx-data)
      (test-util/stub-transact-async ::conn ::tx-data {:db-after ::db-after})
      (test-util/stub-basis-transaction ::db-after ::transaction)
      (stub-tx-instant ::transaction (Instant/ofEpochMilli 0))
      (test-util/stub-basis-t ::db-after "42")

      (let [{:keys [status body]}
            @((handler-intern base-uri ::conn)
               {:body
                {"resourceType" "Bundle"
                 "id" "01a674d5-2a05-43a7-9ed4-b4bd7c676621"
                 "type" "transaction"
                 "entry" entries}})]

        (is (= 200 status))

        (is (= "Bundle" (:resourceType body)))

        (is (= "transaction-response" (:type body)))

        (is (= "200" (-> body :entry first :response :status)))

        (is (= "W/\"42\"" (-> body :entry first :response :etag)))

        (is (= "1970-01-01T00:00:00Z"
               (-> body :entry first :response :lastModified))))))


  (testing "On create in batch"
    (let [resource {"resourceType" "Patient"}
          id #uuid "7973d432-d948-43e8-874e-3f29cf26548e"]

      (test-util/stub-cached-entity ::db-before #{:Patient} some?)
      (stub-squuid id)
      (test-util/stub-upsert-resource ::conn ::db-before 0 (assoc resource "id" (str id)) {:db-after ::db-after})
      (test-util/stub-resource ::db-after #{"Patient"} #{(str id)} #{{:version 0}})
      (test-util/stub-basis-transaction ::db-after ::transaction)
      (stub-tx-instant ::transaction (Instant/ofEpochMilli 0))
      (test-util/stub-basis-t ::db-after "42")

      (let [{:keys [status body]}
            @((handler-intern base-uri ::conn)
               {:body
                {"resourceType" "Bundle"
                 "id" "37984866-e704-4a00-b215-ebd2c9b7e465"
                 "type" "batch"
                 "entry"
                 [{"resource"
                   resource
                   "request"
                   {"method" "POST"
                    "url" "Patient"}}]}})]

        (is (= 200 status))

        (is (= "Bundle" (:resourceType body)))

        (is (= "batch-response" (:type body)))

        (is (= "201" (-> body :entry first :response :status)))

        (is (= (str "http://localhost:8080/fhir/Patient/" id "/_history/42")
               (-> body :entry first :response :location)))

        (is (= "W/\"42\"" (-> body :entry first :response :etag)))

        (is (= "1970-01-01T00:00:00Z"
               (-> body :entry first :response :lastModified))))))


  (testing "On create in transaction with references"
    (let [id #uuid "bc301fe5-262e-4135-846c-7c255db4d6bc"
          entries
          [{"fullUrl" "urn:uuid:9ef14708-5695-4aad-8623-8c8ebd4f48ee"
            "resource"
            {"resourceType" "Observation"
             "subject" {"reference" "urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"}}
            "request"
            {"method" "POST"
             "url" "Observation"}}
           {"fullUrl" "urn:uuid:d7bd0ece-fe3c-4755-b7c9-5b86f42e304a"
            "resource"
            {"resourceType" "Patient"}
            "request"
            {"method" "POST"
             "url" "Patient"}}]
          prepared-entries
          (-> entries
              (assoc-in [0 "resource" "id"] (str id))
              (assoc-in [1 "resource" "id"] (str id)))]

      (test-util/stub-cached-entity ::db-before #{:Patient :Observation} some?)
      (stub-squuid id)
      (stub-tx-data ::db-before #{prepared-entries} ::tx-data)
      (test-util/stub-transact-async ::conn ::tx-data {:db-after ::db-after})
      (test-util/stub-resource ::db-after #{"Patient" "Observation"} #{(str id)} #{{:version 0}})
      (test-util/stub-basis-transaction ::db-after ::transaction)
      (stub-tx-instant ::transaction (Instant/ofEpochMilli 0))
      (test-util/stub-basis-t ::db-after "42")

      (let [{:keys [status body]}
            @((handler-intern base-uri ::conn)
               {:body
                {"resourceType" "Bundle"
                 "id" "37984866-e704-4a00-b215-ebd2c9b7e465"
                 "type" "transaction"
                 "entry" entries}})]

        (is (= 200 status))

        (is (= "Bundle" (:resourceType body)))

        (is (= "transaction-response" (:type body)))

        (is (= "201" (-> body :entry first :response :status)))

        (is (= "201" (-> body :entry second :response :status)))

        (is (= (str "http://localhost:8080/fhir/Observation/" id "/_history/42")
               (-> body :entry first :response :location)))

        (is (= (str "http://localhost:8080/fhir/Patient/" id "/_history/42")
               (-> body :entry second :response :location)))

        (is (= "W/\"42\"" (-> body :entry first :response :etag)))

        (is (= "W/\"42\"" (-> body :entry second :response :etag)))

        (is (= "1970-01-01T00:00:00Z"
               (-> body :entry first :response :lastModified)))

        (is (= "1970-01-01T00:00:00Z"
               (-> body :entry second :response :lastModified)))))))
