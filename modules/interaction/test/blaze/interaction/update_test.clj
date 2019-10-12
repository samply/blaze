(ns blaze.interaction.update-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#update
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.interaction.test-util :as test-util]
    [blaze.interaction.update :refer [handler]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [datomic-spec.test :as dst]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


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
          :transaction-executor #{::transaction-executor}
          :conn #{::conn}
          :term-service #{::term-service}))}})
  (datomic-test-util/stub-db ::conn ::db-before)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest handler-test
  (testing "Returns Error on type mismatch"
    (let [{:keys [status body]}
          @((handler ::transaction-executor ::conn ::term-service)
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {"resourceType" "Observation"}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "invariant" (-> body :issue first :code)))

      (is (= "http://terminology.hl7.org/CodeSystem/operation-outcome"
             (-> body :issue first :details :coding first :system)))

      (is (= "MSG_RESOURCE_TYPE_MISMATCH"
             (-> body :issue first :details :coding first :code)))))


  (testing "Returns Error on ID mismatch"
    (let [{:keys [status body]}
          @((handler ::transaction-executor ::conn ::term-service)
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {"resourceType" "Patient" "id" "1"}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "invariant" (-> body :issue first :code)))

      (is (= "http://terminology.hl7.org/CodeSystem/operation-outcome"
             (-> body :issue first :details :coding first :system)))

      (is (= "MSG_RESOURCE_ID_MISMATCH"
             (-> body :issue first :details :coding first :code)))))


  (testing "On newly created resource"
    (let [resource {"resourceType" "Patient" "id" "0"}]
      (datomic-test-util/stub-resource ::db-before #{"Patient"} #{"0"} nil?)
      (test-util/stub-upsert-resource
        ::transaction-executor
        ::conn
        ::term-service
        ::db-before
        :client-assigned-id
        resource
        (md/success-deferred {:db-after ::db-after}))
      (datomic-test-util/stub-basis-transaction
        ::db-after {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
      (datomic-test-util/stub-pull-resource ::db-after "Patient" "0" #{::resource-after})
      (datomic-test-util/stub-basis-t ::db-after 42)
      (test-util/stub-versioned-instance-url
        ::router "Patient" "0" "42" "location")

      (testing "with no Prefer header"
        (let [{:keys [status headers body]}
              @((handler ::transaction-executor ::conn ::term-service)
                {::reitit/router ::router
                 :path-params {:id "0"}
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :body resource})]

          (testing "Returns 201"
            (is (= 201 status)))

          (testing "Transaction time in Last-Modified header"
            (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 42 is the T of the transaction of the resource update
            (is (= "W/\"42\"" (get headers "ETag"))))

          (testing "Location header"
            (is (= "location" (get headers "Location"))))

          (testing "Contains the resource as body"
            (is (= ::resource-after body)))))

      (testing "with return=minimal Prefer header"
        (let [{:keys [body]}
              @((handler ::transaction-executor ::conn ::term-service)
                {::reitit/router ::router
                 :path-params {:id "0"}
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=minimal"}
                 :body resource})]

          (testing "Contains no body"
            (is (nil? body)))))

      (testing "with return=representation Prefer header"
        (let [{:keys [body]}
              @((handler ::transaction-executor ::conn ::term-service)
                {::reitit/router ::router
                 :path-params {:id "0"}
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=representation"}
                 :body resource})]

          (testing "Contains the resource as body"
            (is (= ::resource-after body)))))

      (testing "with return=OperationOutcome Prefer header"
        (let [{:keys [body]}
              @((handler ::transaction-executor ::conn ::term-service)
                {::reitit/router ::router
                 :path-params {:id "0"}
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=OperationOutcome"}
                 :body resource})]

          (testing "Contains an OperationOutcome as body"
            (is (= {:resourceType "OperationOutcome"} body)))))))


  (testing "On successful update of an existing resource"
    (let [resource {"resourceType" "Patient" "id" "0"}]
      (datomic-test-util/stub-resource ::db-before #{"Patient"} #{"0"} some?)
      (test-util/stub-upsert-resource
        ::transaction-executor
        ::conn
        ::term-service
        ::db-before
        :client-assigned-id
        resource
        (md/success-deferred {:db-after ::db-after}))
      (datomic-test-util/stub-basis-transaction
        ::db-after {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
      (datomic-test-util/stub-pull-resource ::db-after "Patient" "0" #{::resource-after})
      (datomic-test-util/stub-basis-t ::db-after 42)

      (testing "with no Prefer header"
        (let [{:keys [status headers body]}
              @((handler ::transaction-executor ::conn ::term-service)
                {:path-params {:id "0"}
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :body resource})]

          (testing "Returns 200"
            (is (= 200 status)))

          (testing "Transaction time in Last-Modified header"
            (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 42 is the T of the transaction of the resource update
            (is (= "W/\"42\"" (get headers "ETag"))))

          (testing "Contains the resource as body"
            (is (= ::resource-after body)))))

      (testing "with return=minimal Prefer header"
        (let [{:keys [status body]}
              @((handler ::transaction-executor ::conn ::term-service)
                {:path-params {:id "0"}
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=minimal"}
                 :body resource})]

          (testing "Returns 200"
            (is (= 200 status)))

          (testing "Contains no body"
            (is (nil? body)))))

      (testing "with return=representation Prefer header"
        (let [{:keys [status body]}
              @((handler ::transaction-executor ::conn ::term-service)
                {:path-params {:id "0"}
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=representation"}
                 :body resource})]

          (testing "Returns 200"
            (is (= 200 status)))

          (testing "Contains the resource as body"
            (is (= ::resource-after body)))))

      (testing "with return=OperationOutcome Prefer header"
        (let [{:keys [status body]}
              @((handler ::transaction-executor ::conn ::term-service)
                {:path-params {:id "0"}
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=OperationOutcome"}
                 :body resource})]

          (testing "Returns 200"
            (is (= 200 status)))

          (testing "Contains an OperationOutcome as body"
            (is (= {:resourceType "OperationOutcome"} body))))))))
