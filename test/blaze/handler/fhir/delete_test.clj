(ns blaze.handler.fhir.delete-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.datomic.transaction :as tx]
    [blaze.handler.fhir.delete :refer [handler]]
    [blaze.handler.fhir.test-util :as test-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic-spec.test :as dst]
    [taoensso.timbre :as log]))


(st/instrument)
(dst/instrument)


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (st/instrument
    [`handler]
    {:spec
     {`handler
      (s/fspec
        :args (s/cat :conn #{::conn}))}})
  (test-util/stub-db ::conn ::db)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(use-fixtures :each fixture)


(deftest handler-test
  (testing "Returns Not Found on non-existing resource type"
    (test-util/stub-cached-entity ::db #{:Patient} nil?)

    (let [{:keys [status body]}
          @((handler ::conn)
            {:path-params {:type "Patient" :id "0"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns Not Found on non-existing resource"
    (test-util/stub-cached-entity ::db #{:Patient} some?)
    (test-util/stub-resource ::db #{"Patient"} #{"0"} nil?)

    (let [{:keys [status body]}
          @((handler ::conn)
            {:path-params {:type "Patient" :id "0"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns No Content on successful deletion"
    (test-util/stub-cached-entity ::db #{:Patient} some?)
    (test-util/stub-resource ::db #{"Patient"} #{"0"} #{::patient})
    (test-util/stub-resource-deletion ::db "Patient" "0" #{[::tx-data]})
    (test-util/stub-transact-async ::conn [::tx-data] {:db-after ::db-after})
    (test-util/stub-basis-transaction ::db-after {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
    (test-util/stub-basis-t ::db-after 42)

    (let [{:keys [status headers body]}
          @((handler ::conn)
             {:path-params {:type "Patient" :id "0"}})]

      (is (= 204 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 42 is the T of the transaction of the resource update
        (is (= "W/\"42\"" (get headers "ETag"))))

      (is (nil? body))))


  (testing "Returns No Content on already deleted resource"
    (test-util/stub-cached-entity ::db #{:Patient} some?)
    (test-util/stub-resource ::db #{"Patient"} #{"0"} #{::patient})
    (test-util/stub-resource-deletion ::db "Patient" "0" nil?)
    (test-util/stub-basis-transaction ::db {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
    (test-util/stub-basis-t ::db 42)

    (let [{:keys [status headers body]}
          @((handler ::conn)
             {:path-params {:type "Patient" :id "0"}})]

      (is (= 204 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 42 is the T of the transaction of the resource update
        (is (= "W/\"42\"" (get headers "ETag"))))

      (is (nil? body)))))
