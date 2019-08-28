(ns blaze.fhir.response.create-test
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.fhir.response.create :refer :all]
    [blaze.handler.fhir.test-util :as test-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))


(deftest build-created-response-test
  (datomic-test-util/stub-basis-transaction
    ::db {:db/txInstant #inst "2019-05-14T13:58:20.060-00:00"})
  (datomic-test-util/stub-basis-t ::db 42)
  (test-util/stub-versioned-instance-url ::router "Patient" "0" "42" ::location)
  (datomic-test-util/stub-pull-resource ::db "Patient" "0" #{::resource})

  (testing "with no Prefer header"
    (let [{:keys [status headers body]}
          (build-created-response ::router nil? ::db "Patient" "0")]

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


(defn stub-build-created-response
  [router return-preference-spec db type id response]
  (st/instrument
    [`build-created-response]
    {:spec
     {`build-created-response
      (s/fspec
        :args (s/cat :router #{router} :return-preference return-preference-spec
                     :db #{db} :type #{type} :id #{id})
        :ret #{response})}
     :stub
     #{`build-created-response}}))
