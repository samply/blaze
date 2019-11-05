(ns blaze.interaction.delete-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.delete :refer [handler]]
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
          :conn #{::conn}))}})
  (datomic-test-util/stub-db ::conn ::db)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn stub-delete-resource [transaction-executor conn db type id tx-result]
  (st/instrument
    [`fhir-util/delete-resource]
    {:spec
     {`fhir-util/delete-resource
      (s/fspec
        :args
        (s/cat
          :transaction-executor #{transaction-executor}
          :conn #{conn}
          :db #{db}
          :type #{type}
          :id #{id})
        :ret #{tx-result})}
     :stub
     #{`fhir-util/delete-resource}}))


(deftest handler-test
  (testing "Returns Not Found on non-existing resource"
    (datomic-test-util/stub-resource ::db #{"Patient"} #{"0"} nil?)

    (let [{:keys [status body]}
          @((handler ::transaction-executor ::conn)
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns No Content on successful deletion"
    (datomic-test-util/stub-resource ::db #{"Patient"} #{"0"} #{::patient})
    (stub-delete-resource
      ::transaction-executor
      ::conn
      ::db
      "Patient"
      "0"
      (md/success-deferred {:db-after ::db-after}))
    (datomic-test-util/stub-basis-transaction
      ::db-after {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
    (datomic-test-util/stub-basis-t ::db-after 42)

    (let [{:keys [status headers body]}
          @((handler ::transaction-executor ::conn)
             {:path-params {:id "0"}
              ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 204 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 42 is the T of the transaction of the resource update
        (is (= "W/\"42\"" (get headers "ETag"))))

      (is (nil? body))))


  (testing "Returns No Content on already deleted resource"
    (datomic-test-util/stub-resource ::db #{"Patient"} #{"0"} #{::patient})
    (stub-delete-resource
      ::transaction-executor
      ::conn
      ::db
      "Patient"
      "0"
      (md/success-deferred {:db-after ::db}))
    (datomic-test-util/stub-basis-transaction
      ::db {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
    (datomic-test-util/stub-basis-t ::db 42)

    (let [{:keys [status headers body]}
          @((handler ::transaction-executor ::conn)
             {:path-params {:id "0"}
              ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 204 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 42 is the T of the transaction of the resource update
        (is (= "W/\"42\"" (get headers "ETag"))))

      (is (nil? body)))))
