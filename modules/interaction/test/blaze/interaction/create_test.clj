(ns blaze.interaction.create-test
  "Specifications relevant for the FHIR create interaction:

  https://www.hl7.org/fhir/http.html#create
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.create :refer [handler]]
    [blaze.interaction.create-spec]
    [blaze.uuid :refer [random-uuid]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(def router
  (reitit/router
    [["/Patient/{id}" {:name :Patient/instance}]
     ["/Patient/{id}/_history/{vid}" {:name :Patient/versioned-instance}]]
    {:syntax :bracket}))


(defn handler-with [txs]
  (handler (mem-node-with txs)))


(deftest handler-test
  (testing "Returns Error on type mismatch"
    (let [{:keys [status body]}
          @((handler-with [])
            {::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {:resourceType "Observation"}})]

      (is (= 400 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "invariant"
        [:issue 0 :details :coding 0 :system] := "http://terminology.hl7.org/CodeSystem/operation-outcome"
        [:issue 0 :details :coding 0 :code] := "MSG_RESOURCE_TYPE_MISMATCH"
        [:issue 0 :diagnostics] := "Resource type `Observation` doesn't match the endpoint type `Patient`.")))

  (testing "Returns Error on invalid resource"
    (let [{:keys [status body]}
          @((handler-with [])
            {::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {:resourceType "Patient" :gender {}}})]

      (is (= 400 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "invariant"
        [:issue 0 :diagnostics] := "Resource invalid.")))

  (testing "On newly created resource"
    (testing "with no Prefer header"
      (with-redefs
        [random-uuid (constantly #uuid "22de9f47-626a-4fc3-bb69-7bc68401acf4")]
        (let [{:keys [status headers body]}
              @((handler-with [])
                {::reitit/router router
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :body {:resourceType "Patient"}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= "/Patient/22de9f47-626a-4fc3-bb69-7bc68401acf4/_history/1"
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (is (= "Patient" (:resourceType body)))

          (is (= "22de9f47-626a-4fc3-bb69-7bc68401acf4" (:id body))))))

    (testing "with return=minimal Prefer header"
      (with-redefs
        [random-uuid (constantly #uuid "3543b9e8-b237-4daa-9c81-9a99b208aa0d")]
        (let [{:keys [status headers body]}
              @((handler-with [])
                {::reitit/router router
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=minimal"}
                 :body {:resourceType "Patient"}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= "/Patient/3543b9e8-b237-4daa-9c81-9a99b208aa0d/_history/1"
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (is (nil? body)))))

    (testing "with return=representation Prefer header"
      (with-redefs
        [random-uuid (constantly #uuid "d387d53f-358f-48d2-979e-96cb0052b7e2")]
        (let [{:keys [status headers body]}
              @((handler-with [])
                {::reitit/router router
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=representation"}
                 :body {:resourceType "Patient"}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= "/Patient/d387d53f-358f-48d2-979e-96cb0052b7e2/_history/1"
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (is (= "Patient" (:resourceType body))))))

    (testing "with return=OperationOutcome Prefer header"
      (with-redefs
        [random-uuid (constantly #uuid "62a30df4-a4b8-47ed-9203-2d222dd8cdad")]
        (let [{:keys [status headers body]}
              @((handler-with [])
                {::reitit/router router
                 ::reitit/match {:data {:fhir.resource/type "Patient"}}
                 :headers {"prefer" "return=OperationOutcome"}
                 :body {:resourceType "Patient"}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= "/Patient/62a30df4-a4b8-47ed-9203-2d222dd8cdad/_history/1"
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (is (= "OperationOutcome" (:resourceType body))))))))
