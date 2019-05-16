(ns blaze.handler.fhir.read-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#read
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
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
      [`util/cached-entity
       `pull/pull-resource]
      {:spec
       {`util/cached-entity
        (s/fspec
          :args (s/cat :db #{::db} :eid #{:Patient})
          :ret #{::patient-type})
        `pull/pull-resource
        (s/fspec
          :args (s/cat :db #{::db} :type #{"Patient"} :id #{"0"})
          :ret nil?)}
       :stub
       #{`util/cached-entity
         `pull/pull-resource}})

    (let [{:keys [status body]}
          ((handler-intern ::conn)
            {:route-params {:type "Patient" :id "0"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns Existing Resource"
    (let [resource
          (with-meta {"meta" {"versionId" "42"}}
                     {:last-transaction-instant (Instant/ofEpochMilli 0)
                      :version-id "42"})]
      (st/instrument
        [`pull/pull-resource]
        {:spec
         {`pull/pull-resource
          (s/fspec
            :args (s/cat :db #{::db} :type #{"Patient"} :id #{"0"})
            :ret #{resource})}
         :stub
         #{`pull/pull-resource}}))

    (let [{:keys [status headers body]}
          ((handler-intern ::conn)
            {:route-params {:type "Patient" :id "0"}})]

      (is (= 200 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 42 is the T of the transaction of the resource update
        (is (= "W/\"42\"" (get headers "ETag"))))

      (is (= {"meta" {"versionId" "42"}} body)))))
