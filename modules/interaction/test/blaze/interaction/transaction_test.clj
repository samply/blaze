(ns blaze.interaction.transaction-test
  "Specifications relevant for the FHIR batch/transaction interaction:

  https://www.hl7.org/fhir/http.html#transaction
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.bundle :as bundle]
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.datomic.util :as util]
    [blaze.executors :as ex :refer [executor?]]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.test-util :as test-util]
    [blaze.interaction.transaction :refer [handler]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [datomic-spec.test :as dst]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (st/instrument
    [`handler]
    {:spec
     {`handler
      (s/fspec
        :args
        (s/cat
          :conn #{::conn}
          :term-service #{::term-service}
          :executor executor?))}})
  (datomic-test-util/stub-db ::conn ::db-before)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(defonce executor (ex/single-thread-executor))


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


(defn- stub-code-tx-data [db entries-spec result]
  (st/instrument
    [`bundle/code-tx-data]
    {:spec
     {`bundle/code-tx-data
      (s/fspec
        :args (s/cat :db #{db} :entries entries-spec)
        :ret #{result})}
     :stub
     #{`bundle/code-tx-data}}))


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


(defn- stub-annotate-codes [term-service db]
  (st/instrument
    [`bundle/annotate-codes]
    {:spec
     {`bundle/annotate-codes
      (s/fspec
        :args (s/cat :term-service #{term-service} :db #{db} :entries some?))}
     :replace
     {`bundle/annotate-codes
      (fn [_ _ entries] entries)}}))


(defn- given-types-available [& types]
  (datomic-test-util/stub-cached-entity
    ::db-before (into #{} (map keyword) types) some?))


(deftest handler-test
  (testing "Returns Error on missing request"
    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
              "type" "transaction"
              "entry"
              [{}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "value" (-> body :issue first :code)))

      (is (= "Bundle.entry[0]" (-> body :issue first :expression first)))

      (is (= "Missing request." (-> body :issue first :diagnostics)))))

  (testing "Returns Error on missing request url"
    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
              "type" "transaction"
              "entry"
              [{"request" {}}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "value" (-> body :issue first :code)))

      (is (= "Bundle.entry[0].request"
             (-> body :issue first :expression first)))

      (is (= "Missing url." (-> body :issue first :diagnostics)))))

  (testing "Returns Error on missing request method"
    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
              "type" "transaction"
              "entry"
              [{"request"
                {"url" "Patient/0"}}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "value" (-> body :issue first :code)))

      (is (= "Bundle.entry[0].request"
             (-> body :issue first :expression first)))

      (is (= "Missing method." (-> body :issue first :diagnostics)))))

  (testing "Returns Error on unknown method"
    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
              "type" "transaction"
              "entry"
              [{"request"
                {"method" "FOO"
                 "url" "Patient/0"}}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "value" (-> body :issue first :code)))

      (is (= "Bundle.entry[0].request.method"
             (-> body :issue first :expression first)))

      (is (= "Unknown method `FOO`."
             (-> body :issue first :diagnostics)))))

  (testing "Returns Error on unsupported method"
    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
              "type" "transaction"
              "entry"
              [{"request"
                {"method" "PATCH"
                 "url" "Patient/0"}}]}})]

      (is (= 422 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "not-supported" (-> body :issue first :code)))

      (is (= "Bundle.entry[0].request.method"
             (-> body :issue first :expression first)))

      (is (= "Unsupported method `PATCH`."
             (-> body :issue first :diagnostics)))))

  (testing "Returns Error on missing type"
    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
              "type" "transaction"
              "entry"
              [{"request"
                {"method" "PUT"
                 "url" ""}}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "value" (-> body :issue first :code)))

      (is (= "Bundle.entry[0].request.url"
             (-> body :issue first :expression first)))

      (is (= "Can't parse type from `entry.request.url` ``."
             (-> body :issue first :diagnostics)))))

  (testing "Returns Error on unknown type"
    (datomic-test-util/stub-cached-entity ::db-before #{:Foo} nil?)

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
              "type" "transaction"
              "entry"
              [{"request"
                {"method" "PUT"
                 "url" "Foo/0"}}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "value" (-> body :issue first :code)))

      (is (= "Bundle.entry[0].request.url"
             (-> body :issue first :expression first)))

      (is (= "Unknown type `Foo` in bundle entry URL `Foo/0`."
             (-> body :issue first :diagnostics)))))

  (testing "Returns Error on invalid JSON type for resource"
    (given-types-available "Patient")

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
              "type" "transaction"
              "entry"
              [{"resource" []
                "request"
                {"method" "PUT"
                 "url" "Patient/0"}}]}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "structure" (-> body :issue first :code)))

      (is (= "Bundle.entry[0].resource"
             (-> body :issue first :expression first)))

      (is (= "Expected resource of entry 0 to be a JSON Object."
             (-> body :issue first :diagnostics)))))


  (testing "Returns Error on type mismatch of a update"
    (given-types-available "Patient")

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
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

      (is (some #{"Bundle.entry[0].request.url"}
                (-> body :issue first :expression)))

      (is (some #{"Bundle.entry[0].resource.resourceType"}
                (-> body :issue first :expression)))

      (is (= "http://terminology.hl7.org/CodeSystem/operation-outcome"
             (-> body :issue first :details :coding first :system)))

      (is (= "MSG_RESOURCE_TYPE_MISMATCH"
             (-> body :issue first :details :coding first :code)))))


  (testing "Returns Error on ID mismatch of a update"
    (given-types-available "Patient")

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {:body
             {"resourceType" "Bundle"
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

      (is (some #{"Bundle.entry[0].request.url"}
                (-> body :issue first :expression)))

      (is (some #{"Bundle.entry[0].resource.id"}
                (-> body :issue first :expression)))

      (is (= "http://terminology.hl7.org/CodeSystem/operation-outcome"
             (-> body :issue first :details :coding first :system)))

      (is (= "MSG_RESOURCE_ID_MISMATCH"
             (-> body :issue first :details :coding first :code)))))


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

      (given-types-available "Patient")
      (datomic-test-util/stub-resource ::db-before #{"Patient"} #{"0"} nil?)
      (stub-annotate-codes ::term-service ::db-before)
      (stub-code-tx-data ::db-before coll? [])
      (stub-tx-data ::db-before coll? ::tx-data)
      (datomic-test-util/stub-transact-async ::conn ::tx-data {:db-after ::db-after})
      (datomic-test-util/stub-basis-transaction ::db-after ::transaction)
      (stub-tx-instant ::transaction (Instant/ofEpochMilli 0))
      (datomic-test-util/stub-basis-t ::db-after 42)
      (test-util/stub-versioned-instance-url ::router "Patient" "0" "42" ::location)

      (let [{:keys [status body]}
            @((handler ::conn ::term-service executor)
              {::reitit/router ::router
               :body
               {"resourceType" "Bundle"
                "type" "transaction"
                "entry" entries}})]

        (is (= 200 status))

        (is (= "Bundle" (:resourceType body)))

        (is (= "transaction-response" (:type body)))

        (is (= "201" (-> body :entry first :response :status)))

        (is (= ::location (-> body :entry first :response :location)))

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

      (given-types-available "Patient")
      (datomic-test-util/stub-resource ::db-before #{"Patient"} #{"0"} #{::old-patient})
      (stub-annotate-codes ::term-service ::db-before)
      (stub-code-tx-data ::db-before coll? [])
      (stub-tx-data ::db-before coll? ::tx-data)
      (datomic-test-util/stub-transact-async
        ::conn ::tx-data (md/success-deferred {:db-after ::db-after}))
      (datomic-test-util/stub-basis-transaction ::db-after ::transaction)
      (stub-tx-instant ::transaction (Instant/ofEpochMilli 0))
      (datomic-test-util/stub-basis-t ::db-after 42)

      (testing "with no Prefer header"
        (let [{:keys [status body]}
              @((handler ::conn ::term-service executor)
                {:body
                 {"resourceType" "Bundle"
                  "type" "transaction"
                  "entry" entries}})]

          (is (= 200 status))

          (is (= "Bundle" (:resourceType body)))

          (is (= "transaction-response" (:type body)))

          (is (= "200" (-> body :entry first :response :status)))

          (is (= "W/\"42\"" (-> body :entry first :response :etag)))

          (is (= "1970-01-01T00:00:00Z"
                 (-> body :entry first :response :lastModified)))

          (testing "there is no resource embedded in the entry"
            (is (nil? (-> body :entry first :resource))))))))


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
             "url" "Patient"}}]]

      (given-types-available "Patient" "Observation")
      (datomic-test-util/stub-squuid id)
      (stub-annotate-codes ::term-service ::db-before)
      (stub-code-tx-data ::db-before coll? [])
      (stub-tx-data ::db-before coll? ::tx-data)
      (datomic-test-util/stub-transact-async ::conn ::tx-data {:db-after ::db-after})
      (datomic-test-util/stub-resource
        ::db-after #{"Patient" "Observation"} #{(str id)} #{{:instance/version 0}})
      (datomic-test-util/stub-basis-transaction ::db-after ::transaction)
      (stub-tx-instant ::transaction (Instant/ofEpochMilli 0))
      (datomic-test-util/stub-basis-t ::db-after 42)
      (test-util/stub-versioned-instance-url ::router "Patient" (str id) "42" ::location)
      (st/instrument
        [`fhir-util/versioned-instance-url]
        {:spec
         {`fhir-util/versioned-instance-url
          (s/fspec
            :args (s/cat :router #{::router} :type string? :id string?
                         :vid string?))}
         :replace
         {`fhir-util/versioned-instance-url
          (fn [_ type _ _]
            (keyword "location" type))}})

      (let [{:keys [status body]}
            @((handler ::conn ::term-service executor)
              {::reitit/router ::router
               :body
               {"resourceType" "Bundle"
                "type" "transaction"
                "entry" entries}})]

        (is (= 200 status))

        (is (= "Bundle" (:resourceType body)))

        (is (= "transaction-response" (:type body)))

        (is (= "201" (-> body :entry first :response :status)))

        (is (= "201" (-> body :entry second :response :status)))

        (is (= :location/Observation (-> body :entry first :response :location)))

        (is (= :location/Patient (-> body :entry second :response :location)))

        (is (= "W/\"42\"" (-> body :entry first :response :etag)))

        (is (= "W/\"42\"" (-> body :entry second :response :etag)))

        (is (= "1970-01-01T00:00:00Z"
               (-> body :entry first :response :lastModified)))

        (is (= "1970-01-01T00:00:00Z"
               (-> body :entry second :response :lastModified)))))))


(defn- stub-match-by-path [router path match]
  (st/instrument
    [`reitit/match-by-path]
    {:spec
     {`reitit/match-by-path
      (s/fspec
        :args (s/cat :router #{router} :path #{path})
        :ret #{match})}
     :stub
     #{`reitit/match-by-path}}))


(deftest handler-batch-create-test
  (given-types-available "Patient")

  (testing "Successful"
    (let [handler
          (fn [{:keys [body]}]
            (is (= {"resourceType" "Patient"} body))
            (md/success-deferred
              (-> (ring/created "location" ::response-body)
                  (ring/header "Last-Modified" "Mon, 24 Jun 2019 09:54:26 GMT")
                  (ring/header "ETag" "etag"))))]
      (stub-match-by-path
        ::router "Patient" {:result {:post {:handler handler}}}))

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {::reitit/router ::router
             :body
             {"resourceType" "Bundle"
              "type" "batch"
              "entry"
              [{"resource"
                {"resourceType" "Patient"}
                "request"
                {"method" "POST"
                 "url" "Patient"}}]}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "batch-response" (:type body)))

      (is (= "201" (-> body :entry first :response :status)))

      (is (= "location" (-> body :entry first :response :location)))

      (is (= "etag" (-> body :entry first :response :etag)))

      (is (= "2019-06-24T09:54:26Z" (-> body :entry first :response :lastModified)))

      (is (= ::response-body (-> body :entry first :resource)))))

  (testing "Failing"
    (let [handler
          (fn [_]
            (md/success-deferred
              (ring/bad-request ::operation-outcome)))]
      (stub-match-by-path
        ::router "Patient" {:result {:post {:handler handler}}}))

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {::reitit/router ::router
             :body
             {"resourceType" "Bundle"
              "type" "batch"
              "entry"
              [{"resource"
                {"resourceType" "Patient"}
                "request"
                {"method" "POST"
                 "url" "Patient"}}]}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "batch-response" (:type body)))

      (is (= "400" (-> body :entry first :response :status)))

      (is (= ::operation-outcome (-> body :entry first :response :outcome))))))


(deftest handler-batch-read-test
  (given-types-available "Patient")

  (testing "Successful"
    (let [handler
          (fn [_]
            (md/success-deferred
              (-> (ring/response ::response-body)
                  (ring/header "Last-Modified" "Mon, 24 Jun 2019 09:54:26 GMT")
                  (ring/header "ETag" "etag"))))]
      (stub-match-by-path
        ::router "Patient/0" {:result {:get {:handler handler}}}))

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {::reitit/router ::router
             :body
             {"resourceType" "Bundle"
              "type" "batch"
              "entry"
              [{"request"
                {"method" "GET"
                 "url" "Patient/0"}}]}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "batch-response" (:type body)))

      (is (= "200" (-> body :entry first :response :status)))

      (is (= "etag" (-> body :entry first :response :etag)))

      (is (= "2019-06-24T09:54:26Z" (-> body :entry first :response :lastModified)))

      (is (= ::response-body (-> body :entry first :resource)))))

  (testing "Failing"
    (let [handler
          (fn [_]
            (md/success-deferred
              (ring/bad-request ::operation-outcome)))]
      (stub-match-by-path
        ::router "Patient/0" {:result {:get {:handler handler}}}))

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {::reitit/router ::router
             :body
             {"resourceType" "Bundle"
              "type" "batch"
              "entry"
              [{"request"
                {"method" "GET"
                 "url" "Patient/0"}}]}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "batch-response" (:type body)))

      (is (= "400" (-> body :entry first :response :status)))

      (is (= ::operation-outcome (-> body :entry first :response :outcome))))))


(deftest handler-batch-search-type-test
  (given-types-available "Patient")

  (testing "Successful"
    (let [handler
          (fn [_]
            (md/success-deferred
              (ring/response ::response-body)))]
      (stub-match-by-path
        ::router "Patient" {:result {:get {:handler handler}}}))

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {::reitit/router ::router
             :body
             {"resourceType" "Bundle"
              "type" "batch"
              "entry"
              [{"request"
                {"method" "GET"
                 "url" "Patient"}}]}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "batch-response" (:type body)))

      (is (= "200" (-> body :entry first :response :status)))

      (is (= ::response-body (-> body :entry first :resource))))))


(deftest handler-batch-update-test
  (given-types-available "Patient")

  (testing "Successful"
    (let [handler
          (fn [{:keys [body]}]
            (is (= {"resourceType" "Patient"} body))
            (md/success-deferred
              (-> (ring/response ::response-body)
                  (ring/header "Last-Modified" "Mon, 24 Jun 2019 09:54:26 GMT")
                  (ring/header "ETag" "etag"))))]
      (stub-match-by-path
        ::router "Patient/0" {:result {:put {:handler handler}}}))

    (let [{:keys [status body]}
          @((handler ::conn ::term-service executor)
            {::reitit/router ::router
             :body
             {"resourceType" "Bundle"
              "type" "batch"
              "entry"
              [{"resource"
                {"resourceType" "Patient"}
                "request"
                {"method" "PUT"
                 "url" "Patient/0"}}]}})]

      (is (= 200 status))

      (is (= "Bundle" (:resourceType body)))

      (is (= "batch-response" (:type body)))

      (is (= "200" (-> body :entry first :response :status)))

      (is (= "etag" (-> body :entry first :response :etag)))

      (is (= "2019-06-24T09:54:26Z" (-> body :entry first :response :lastModified)))

      (is (= ::response-body (-> body :entry first :resource))))))
