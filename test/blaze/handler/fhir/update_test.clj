(ns blaze.handler.fhir.update-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#update
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.handler.fhir.test-util :as test-util]
    [blaze.handler.fhir.update :refer [handler-intern]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic-spec.test :as dst]))


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


(deftest handler-test
  (testing "Returns Error on type mismatch"
    (test-util/stub-cached-entity ::db-before #{:Patient} some?)

    (let [{:keys [status body]}
          @((handler-intern base-uri ::conn)
             {:route-params {:type "Patient" :id "0"}
              :body {"resourceType" "Observation"}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "invariant" (-> body :issue first :code)))

      (is (= "http://terminology.hl7.org/CodeSystem/operation-outcome"
             (-> body :issue first :details :coding first :system)))

      (is (= "MSG_RESOURCE_TYPE_MISMATCH"
             (-> body :issue first :details :coding first :code)))))


  (testing "Returns Error on ID mismatch"
    (test-util/stub-cached-entity ::db-before #{:Patient} some?)

    (let [{:keys [status body]}
          @((handler-intern base-uri ::conn)
             {:route-params {:type "Patient" :id "0"}
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
      (test-util/stub-cached-entity ::db-before #{:Patient} some?)
      (test-util/stub-resource ::db-before #{"Patient"} #{"0"} nil?)
      (test-util/stub-upsert-resource ::conn ::db-before -2 resource {:db-after ::db-after})
      (test-util/stub-basis-transaction ::db-after {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
      (test-util/stub-pull-resource ::db-after "Patient" "0" #{::resource-after})
      (test-util/stub-basis-t ::db-after "42")

      (testing "with no Prefer header"
        (let [{:keys [status headers body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient" :id "0"}
                  :body resource})]

          (testing "Returns 201"
            (is (= 201 status)))

          (testing "Transaction time in Last-Modified header"
            (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 42 is the T of the transaction of the resource update
            (is (= "W/\"42\"" (get headers "ETag"))))

          (testing "Location header"
            (is (= "http://localhost:8080/fhir/Patient/0" (get headers "Location"))))

          (testing "Contains the resource as body"
            (is (= ::resource-after body)))))

      (testing "with return=minimal Prefer header"
        (let [{:keys [body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient" :id "0"}
                  :headers {"prefer" "return=minimal"}
                  :body resource})]

          (testing "Contains no body"
            (is (nil? body)))))

      (testing "with return=representation Prefer header"
        (let [{:keys [body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient" :id "0"}
                  :headers {"prefer" "return=representation"}
                  :body resource})]

          (testing "Contains the resource as body"
            (is (= ::resource-after body)))))

      (testing "with return=OperationOutcome Prefer header"
        (let [{:keys [body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient" :id "0"}
                  :headers {"prefer" "return=OperationOutcome"}
                  :body resource})]

          (testing "Contains an OperationOutcome as body"
            (is (= {:resourceType "OperationOutcome"} body)))))))


  (testing "On successful update of an existing resource"
    (let [resource {"resourceType" "Patient" "id" "0"}]
      (test-util/stub-cached-entity ::db-before #{:Patient} some?)
      (test-util/stub-resource ::db-before #{"Patient"} #{"0"} some?)
      (test-util/stub-upsert-resource ::conn ::db-before -2 resource {:db-after ::db-after})
      (test-util/stub-basis-transaction ::db-after {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
      (test-util/stub-pull-resource ::db-after "Patient" "0" #{::resource-after})
      (test-util/stub-basis-t ::db-after "42")

      (testing "with no Prefer header"
        (let [{:keys [status headers body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient" :id "0"}
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
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient" :id "0"}
                  :headers {"prefer" "return=minimal"}
                  :body resource})]

          (testing "Returns 200"
            (is (= 200 status)))

          (testing "Contains no body"
            (is (nil? body)))))

      (testing "with return=representation Prefer header"
        (let [{:keys [status body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient" :id "0"}
                  :headers {"prefer" "return=representation"}
                  :body resource})]

          (testing "Returns 200"
            (is (= 200 status)))

          (testing "Contains the resource as body"
            (is (= ::resource-after body)))))

      (testing "with return=OperationOutcome Prefer header"
        (let [{:keys [status body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient" :id "0"}
                  :headers {"prefer" "return=OperationOutcome"}
                  :body resource})]

          (testing "Returns 200"
            (is (= 200 status)))

          (testing "Contains an OperationOutcome as body"
            (is (= {:resourceType "OperationOutcome"} body))))))))
