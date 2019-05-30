(ns blaze.handler.fhir.read-test
  "Specifications relevant for the FHIR read interaction:

  https://www.hl7.org/fhir/http.html#read
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.test-util :as test-util]
    [blaze.handler.fhir.read :refer [handler-intern]]
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
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Resource Type"
    (test-util/stub-db ::conn ::db)
    (test-util/stub-cached-entity ::db #{:Patient} nil?)

    (let [{:keys [status body]}
          @((handler-intern ::conn)
            {:route-params {:type "Patient" :id "0"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns Not Found on Non-Existing Resource"
    (test-util/stub-db ::conn ::db)
    (test-util/stub-cached-entity ::db #{:Patient} some?)
    (test-util/stub-pull-resource ::db "Patient" "0" nil?)

    (let [{:keys [status body]}
          @((handler-intern ::conn)
            {:route-params {:type "Patient" :id "0"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns Not Found on Invalid Version ID"
    (let [{:keys [status body]}
          @((handler-intern ::conn)
             {:route-params {:type "Patient" :id "0" :vid "a"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns Gone on Deleted Resource"
    (let [resource
          (with-meta {"meta" {"versionId" "42"}}
                     {:last-transaction-instant (Instant/ofEpochMilli 0)
                      :version-id "42"
                      :deleted true})]
      (test-util/stub-db ::conn ::db)
      (test-util/stub-cached-entity ::db #{:Patient} some?)
      (test-util/stub-pull-resource ::db "Patient" "0" #{resource})

      (let [{:keys [status body headers]}
            @((handler-intern ::conn)
              {:route-params {:type "Patient" :id "0"}})]

        (is (= 410 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 42 is the T of the transaction of the resource update
          (is (= "W/\"42\"" (get headers "ETag"))))

        (is (= "OperationOutcome" (:resourceType body)))

        (is (= "error" (-> body :issue first :severity)))

        (is (= "deleted" (-> body :issue first :code))))))


  (testing "Returns Existing Resource"
    (let [resource
          (with-meta {"meta" {"versionId" "42"}}
                     {:last-transaction-instant (Instant/ofEpochMilli 0)
                      :version-id "42"})]
      (test-util/stub-db ::conn ::db)
      (test-util/stub-cached-entity ::db #{:Patient} some?)
      (test-util/stub-pull-resource ::db "Patient" "0" #{resource})

      (let [{:keys [status headers body]}
            @((handler-intern ::conn)
               {:route-params {:type "Patient" :id "0"}})]

        (is (= 200 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 42 is the T of the transaction of the resource update
          (is (= "W/\"42\"" (get headers "ETag"))))

        (is (= {"meta" {"versionId" "42"}} body)))))


  (testing "Returns Existing Resource on versioned read"
    (let [resource
          (with-meta {"meta" {"versionId" "42"}}
                     {:last-transaction-instant (Instant/ofEpochMilli 0)
                      :version-id "42"})]
      (test-util/stub-sync ::conn 42 ::db)
      (test-util/stub-as-of ::db 42 ::as-of-db)
      (test-util/stub-cached-entity ::as-of-db #{:Patient} some?)
      (test-util/stub-pull-resource ::as-of-db "Patient" "0" #{resource})

      (let [{:keys [status headers body]}
            @((handler-intern ::conn)
               {:route-params {:type "Patient" :id "0" :vid "42"}})]

        (is (= 200 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 42 is the T of the transaction of the resource update
          (is (= "W/\"42\"" (get headers "ETag"))))

        (is (= {"meta" {"versionId" "42"}} body))))))
