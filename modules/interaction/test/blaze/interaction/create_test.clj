(ns blaze.interaction.create-test
  "Specifications relevant for the FHIR create interaction:

  https://www.hl7.org/fhir/http.html#create
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.create :refer [handler]]
    [blaze.middleware.fhir.metrics-spec]
    [blaze.uuid :refer [random-uuid]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(def router
  (reitit/router
    [["/Patient/{id}" {:name :Patient/instance}]]
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

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "invariant" (-> body :issue first :code)))

      (is (= "http://terminology.hl7.org/CodeSystem/operation-outcome"
             (-> body :issue first :details :coding first :system)))

      (is (= "MSG_RESOURCE_TYPE_MISMATCH"
             (-> body :issue first :details :coding first :code)))

      (is (= "Resource type `Observation` doesn't match the endpoint type `Patient`."
             (-> body :issue first :diagnostics)))))

  (testing "Returns Error on invalid resource"
    (let [{:keys [status body]}
          @((handler-with [])
            {::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {:resourceType "Patient" :gender {}}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "invariant" (-> body :issue first :code)))

      (is (= "Resource invalid." (-> body :issue first :diagnostics)))))

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

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (is (= "Patient" (:resourceType body)))

          (is (= "22de9f47-626a-4fc3-bb69-7bc68401acf4" (:id body))))))

    (testing "with return=minimal Prefer header"
      (let [{:keys [status headers body]}
            @((handler-with [])
              {::reitit/router router
               ::reitit/match {:data {:fhir.resource/type "Patient"}}
               :headers {"prefer" "return=minimal"}
               :body {:resourceType "Patient"}})]

        (is (= 201 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (is (nil? body))))

    (testing "with return=representation Prefer header"
      (let [{:keys [status headers body]}
            @((handler-with [])
              {::reitit/router router
               ::reitit/match {:data {:fhir.resource/type "Patient"}}
               :headers {"prefer" "return=representation"}
               :body {:resourceType "Patient"}})]

        (is (= 201 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (is (= "Patient" (:resourceType body)))))

    (testing "with return=OperationOutcome Prefer header"
      (let [{:keys [status headers body]}
            @((handler-with [])
              {::reitit/router router
               ::reitit/match {:data {:fhir.resource/type "Patient"}}
               :headers {"prefer" "return=OperationOutcome"}
               :body {:resourceType "Patient"}})]

        (is (= 201 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (is (= "OperationOutcome" (:resourceType body)))))))
