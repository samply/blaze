(ns blaze.interaction.read-test
  "Specifications relevant for the FHIR read interaction:

  https://www.hl7.org/fhir/http.html#read
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.interaction.read :refer [handler]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [datomic-spec.test :as dst]
    [reitit.core :as reitit]
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
        :args (s/cat :conn #{::conn}))}})
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Resource"
    (datomic-test-util/stub-db ::conn ::db)
    (datomic-test-util/stub-pull-resource ::db "Patient" "0" nil?)

    (let [{:keys [status body]}
          @((handler ::conn)
             {:path-params {:id "0"}
              ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns Not Found on Invalid Version ID"
    (let [{:keys [status body]}
          @((handler ::conn)
             {:path-params {:id "0" :vid "a"}
              ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

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
      (datomic-test-util/stub-db ::conn ::db)
      (datomic-test-util/stub-pull-resource ::db "Patient" "0" #{resource})

      (let [{:keys [status body headers]}
            @((handler ::conn)
               {:path-params {:id "0"}
                ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

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
      (datomic-test-util/stub-db ::conn ::db)
      (datomic-test-util/stub-pull-resource ::db "Patient" "0" #{resource})

      (let [{:keys [status headers body]}
            @((handler ::conn)
               {:path-params {:id "0"}
                ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

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
      (datomic-test-util/stub-sync ::conn 42 ::db)
      (datomic-test-util/stub-as-of ::db 42 ::as-of-db)
      (datomic-test-util/stub-pull-resource ::as-of-db "Patient" "0" #{resource})

      (let [{:keys [status headers body]}
            @((handler ::conn)
               {:path-params {:id "0" :vid "42"}
                ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 200 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 42 is the T of the transaction of the resource update
          (is (= "W/\"42\"" (get headers "ETag"))))

        (is (= {"meta" {"versionId" "42"}} body))))))
