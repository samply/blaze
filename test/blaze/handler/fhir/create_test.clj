(ns blaze.handler.fhir.create-test
  "Specifications relevant for the FHIR create interaction:

  https://www.hl7.org/fhir/http.html#create
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.create :refer [handler-intern]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [datomic-spec.test :as dst]))


(st/instrument)
(dst/instrument)


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(def base-uri "http://localhost:8080")


(deftest handler-test
  (testing "Returns Error on type mismatch"
    (let [{:keys [status body]}
          @((handler-intern base-uri ::conn)
             {:route-params {:type "Patient"}
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
      (st/instrument
        [`d/basis-t
         `d/db
         `d/squuid
         `pull/pull-resource
         `tx/resource-update
         `tx/transact-async
         `util/basis-transaction]
        {:spec
         {`d/basis-t
          (s/fspec
            :args (s/cat :db #{::db-after})
            :ret #{"42"})
          `d/db
          (s/fspec
            :args (s/cat :conn #{::conn})
            :ret #{::db-before})
          `d/squuid
          (s/fspec
            :args (s/cat)
            :ret #{id})
          `pull/pull-resource
          (s/fspec
            :args (s/cat :db #{::db-after} :type #{"Patient"} :id #{(str id)})
            :ret #{::resource-after})
          `tx/resource-update
          (s/fspec
            :args (s/cat :db #{::db-before}
                         :resource #{{"resourceType" "Patient" "id" (str id)}})
            :ret #{::resource-tx-data})
          `tx/transact-async
          (s/fspec
            :args (s/cat :conn #{::conn} :tx-data #{::resource-tx-data})
            :ret #{{:db-after ::db-after}})
          `util/basis-transaction
          (s/fspec
            :args (s/cat :db #{::db-after})
            :ret #{{:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"}})}
         :stub
         #{`d/basis-t
           `d/db
           `d/squuid
           `pull/pull-resource
           `tx/resource-update
           `tx/transact-async
           `util/basis-transaction}})

      (testing "with no Prefer header"
        (let [{:keys [status headers body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient"}
                  :body {"resourceType" "Patient"}})]

          (testing "Returns 201"
            (is (= 201 status)))

          (testing "Transaction time in Last-Modified header"
            (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 42 is the T of the transaction of the resource update
            (is (= "W/\"42\"" (get headers "ETag"))))

          (testing "Location header"
            (is (= (str "http://localhost:8080/fhir/Patient/" id "/_history/42")
                   (get headers "Location"))))

          (testing "Contains the resource as body"
            (is (= ::resource-after body)))))

      (testing "with return=minimal Prefer header"
        (let [{:keys [body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient"}
                  :headers {"prefer" "return=minimal"}
                  :body {"resourceType" "Patient"}})]

          (testing "Contains no body"
            (is (nil? body)))))

      (testing "with return=representation Prefer header"
        (let [{:keys [body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient"}
                  :headers {"prefer" "return=representation"}
                  :body {"resourceType" "Patient"}})]

          (testing "Contains the resource as body"
            (is (= ::resource-after body)))))

      (testing "with return=OperationOutcome Prefer header"
        (let [{:keys [body]}
              @((handler-intern base-uri ::conn)
                 {:route-params {:type "Patient"}
                  :headers {"prefer" "return=OperationOutcome"}
                  :body {"resourceType" "Patient"}})]

          (testing "Contains an OperationOutcome as body"
            (is (= {:resourceType "OperationOutcome"} body))))))))
