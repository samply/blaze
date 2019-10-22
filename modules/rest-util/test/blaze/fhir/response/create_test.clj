(ns blaze.fhir.response.create-test
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.fhir.response.create :refer [build-created-response]]
    [blaze.handler.fhir.util :as fhir-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (st/instrument
    [`build-created-response]
    {:spec
     {`build-created-response
      (s/fspec
        :args (s/cat :router #{::router} :return-preference (s/nilable string?)
                     :db #{::db} :type string? :id string?))}})
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn stub-versioned-instance-url [router type id vid url]
  (st/instrument
    [`fhir-util/versioned-instance-url]
    {:spec
     {`fhir-util/versioned-instance-url
      (s/fspec
        :args (s/cat :router #{router} :type #{type} :id #{id} :vid #{vid})
        :ret #{url})}
     :stub
     #{`fhir-util/versioned-instance-url}}))


(deftest build-created-response-test
  (datomic-test-util/stub-basis-transaction
    ::db {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
  (datomic-test-util/stub-basis-t ::db 42)
  (stub-versioned-instance-url ::router "Patient" "0" "42" ::location)
  (datomic-test-util/stub-pull-resource ::db "Patient" "0" #{::resource})

  (testing "with no Prefer header"
    (let [{:keys [status headers body]}
          (build-created-response ::router nil ::db "Patient" "0")]

      (testing "Returns 201"
        (is (= 201 status)))

      (testing "Transaction time in Last-Modified header"
        (is (= "Tue, 14 May 2019 13:58:20 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 42 is the T of the transaction of the resource update
        (is (= "W/\"42\"" (get headers "ETag"))))

      (testing "Location header"
        (is (= ::location (get headers "Location"))))

      (testing "Contains the resource as body"
        (is (= ::resource body)))))

  (testing "with return=minimal Prefer header"
    (let [{:keys [body]}
          (build-created-response ::router "minimal" ::db "Patient" "0")]

      (testing "Contains no body"
        (is (nil? body)))))

  (testing "with return=representation Prefer header"
    (let [{:keys [body]}
          (build-created-response ::router "representation" ::db "Patient" "0")]

      (testing "Contains the resource as body"
        (is (= ::resource body))))))
