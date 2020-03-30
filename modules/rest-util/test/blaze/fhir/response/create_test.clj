(ns blaze.fhir.response.create-test
  (:require
    [blaze.db.api-stub :as api-stub]
    [blaze.fhir.response.create :refer [build-created-response]]
    [blaze.handler.fhir.util-stub :as fhir-util-stub]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


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


(deftest build-created-response-test
  (let [tx {:blaze.db.tx/instant (Instant/ofEpochMilli 0)}
        resource (with-meta {:id "175947" :meta {:versionId "42"}} {:blaze.db/tx tx})]

    (api-stub/resource ::db "Patient" "0" #{resource})
    (fhir-util-stub/versioned-instance-url
      ::router "Patient" "0" "42" ::location)


    (testing "with no Prefer header"
      (let [{:keys [status headers body]}
            (build-created-response ::router nil ::db "Patient" "0")]

        (testing "Returns 201"
          (is (= 201 status)))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 42 is the T of the transaction of the resource update
          (is (= "W/\"42\"" (get headers "ETag"))))

        (testing "Location header"
          (is (= ::location (get headers "Location"))))

        (testing "Contains the resource as body"
          (is (= resource body)))))

    (testing "with return=minimal Prefer header"
      (let [{:keys [body]}
            (build-created-response ::router "minimal" ::db "Patient" "0")]

        (testing "Contains no body"
          (is (nil? body)))))


    (testing "with return=representation Prefer header"
      (let [{:keys [body]}
            (build-created-response ::router "representation" ::db "Patient" "0")]

        (testing "Contains the resource as body"
          (is (= resource body)))))))
