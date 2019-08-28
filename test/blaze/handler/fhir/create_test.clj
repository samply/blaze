(ns blaze.handler.fhir.create-test
  "Specifications relevant for the FHIR create interaction:

  https://www.hl7.org/fhir/http.html#create
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.handler.fhir.create :refer [handler]]
    [blaze.handler.fhir.test-util :as fhir-test-util]
    [blaze.fhir.response.create-test :as create-test]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
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
        :args (s/cat :conn #{::conn} :term-service #{::term-service}))}})
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(use-fixtures :each fixture)


(deftest handler-test
  (testing "Returns Error on type mismatch"
    (let [{:keys [status body]}
          @((handler ::conn ::term-service)
            {:path-params {:type "Patient"}
             :body {"resourceType" "Observation"}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "invariant" (-> body :issue first :code)))

      (is (= "http://terminology.hl7.org/CodeSystem/operation-outcome"
             (-> body :issue first :details :coding first :system)))

      (is (= "MSG_RESOURCE_TYPE_MISMATCH"
             (-> body :issue first :details :coding first :code)))))


  (testing "On newly created resource"
    (let [id #uuid "6f9c4f5e-a9b3-40fb-871c-7b0ccddb3c99"]
      (datomic-test-util/stub-db ::conn ::db-before)
      (datomic-test-util/stub-squuid id)
      (fhir-test-util/stub-upsert-resource
        ::conn ::term-service ::db-before :server-assigned-id
        {"resourceType" "Patient" "id" (str id)}
        (md/success-deferred {:db-after ::db-after}))

      (testing "with no Prefer header"
        (create-test/stub-build-created-response
          ::router nil? ::db-after "Patient" (str id) ::response)

        (is (= ::response
               @((handler ::conn ::term-service)
                 {::reitit/router ::router
                  :path-params {:type "Patient"}
                  :body {"resourceType" "Patient"}}))))

      (testing "with return=minimal Prefer header"
        (create-test/stub-build-created-response
          ::router #{"minimal"} ::db-after "Patient" (str id) ::response)

        (is (= ::response
               @((handler ::conn ::term-service)
                 {::reitit/router ::router
                  :path-params {:type "Patient"}
                  :headers {"prefer" "return=minimal"}
                  :body {"resourceType" "Patient"}}))))

      (testing "with return=representation Prefer header"
        (create-test/stub-build-created-response
          ::router #{"representation"} ::db-after "Patient" (str id) ::response)

        (is (= ::response
               @((handler ::conn ::term-service)
                 {::reitit/router ::router
                  :path-params {:type "Patient"}
                  :headers {"prefer" "return=representation"}
                  :body {"resourceType" "Patient"}}))))

      (testing "with return=OperationOutcome Prefer header"
        (create-test/stub-build-created-response
          ::router #{"OperationOutcome"} ::db-after "Patient" (str id) ::response)

        (is (= ::response
               @((handler ::conn ::term-service)
                 {::reitit/router ::router
                  :path-params {:type "Patient"}
                  :headers {"prefer" "return=OperationOutcome"}
                  :body {"resourceType" "Patient"}})))))))
