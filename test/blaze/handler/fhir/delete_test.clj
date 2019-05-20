(ns blaze.handler.fhir.delete-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.delete :refer [handler-intern]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [blaze.datomic.transaction :as tx])
  (:import
    [java.time Instant]))


(st/instrument)
(dst/instrument)


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (st/instrument
    [`d/db]
    {:spec
     {`d/db
      (s/fspec
        :args (s/cat :conn #{::conn})
        :ret #{::db})}
     :stub
     #{`d/db}})
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Resource Type"
    (st/instrument
      [`util/cached-entity]
      {:spec
       {`util/cached-entity
        (s/fspec
          :args (s/cat :db #{::db} :eid #{:Patient})
          :ret nil?)}
       :stub
       #{`util/cached-entity}})

    (let [{:keys [status body]}
          ((handler-intern ::conn)
            {:route-params {:type "Patient" :id "0"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns Not Found on Non-Existing Resource"
    (st/instrument
      [`d/entity
       `util/cached-entity]
      {:spec
       {`d/entity
        (s/fspec
          :args (s/cat :db #{::db} :eid #{[:Patient/id "0"]})
          :ret nil?)
        `util/cached-entity
        (s/fspec
          :args (s/cat :db #{::db} :eid #{:Patient})
          :ret #{::patient-type})}
       :stub
       #{`d/entity
         `util/cached-entity}})

    (let [{:keys [status body]}
          ((handler-intern ::conn)
            {:route-params {:type "Patient" :id "0"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns No Content on Successful Deletion"
    (st/instrument
      [`d/basis-t
       `d/entity
       `tx/resource-deletion
       `tx/transact-async
       `util/basis-transaction
       `util/cached-entity]
      {:spec
       {`d/basis-t
        (s/fspec
          :args (s/cat :db #{::db-after})
          :ret #{"42"})
        `d/entity
        (s/fspec
          :args (s/cat :db #{::db} :eid #{[:Patient/id "0"]})
          :ret #{::patient})
        `tx/resource-deletion
        (s/fspec
          :args (s/cat :db #{::db} :type #{"Patient"} :id #{"0"})
          :ret #{::resource-tx-data})
        `tx/transact-async
        (s/fspec
          :args (s/cat :conn #{::conn} :tx-data #{::resource-tx-data})
          :ret #{{:db-after ::db-after}})
        `util/basis-transaction
        (s/fspec
          :args (s/cat :db #{::db-after})
          :ret #{{:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"}})
        `util/cached-entity
        (s/fspec
          :args (s/cat :db #{::db} :eid #{:Patient})
          :ret #{::patient-type})}
       :stub
       #{`d/basis-t
         `d/entity
         `tx/resource-deletion
         `tx/transact-async
         `util/basis-transaction
         `util/cached-entity}})

    (let [{:keys [status headers body]}
          @((handler-intern ::conn)
             {:route-params {:type "Patient" :id "0"}})]

      (is (= 204 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 42 is the T of the transaction of the resource update
        (is (= "W/\"42\"" (get headers "ETag"))))

      (is (nil? body)))))
