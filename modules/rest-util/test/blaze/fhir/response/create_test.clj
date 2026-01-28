(ns blaze.fhir.response.create-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.response.create :refer [build-response]]
   [blaze.fhir.response.create-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def router
  (reitit/router
   [["/Patient" {:name :Patient/type}]]
   {:syntax :bracket}))

(def context
  {:blaze/base-url "http://localhost:8080" ::reitit/router router})

(deftest build-response-test
  (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

    (let [db (d/db node)
          context (assoc context :blaze/db db)
          resource-handle (d/resource-handle db "Patient" "0")
          resource @(d/pull db resource-handle)]

      (testing "created"
        (testing "with no Prefer header"
          (let [{:keys [status headers body]}
                @(build-response context nil nil resource-handle)]

            (testing "Returns 201"
              (is (= 201 status)))

            (testing "Transaction time in Last-Modified header"
              (is (= "Thu, 1 Jan 1970 00:00:00 GMT"
                     (get headers "Last-Modified"))))

            (testing "Version in ETag header"
              ;; 1 is the T of the transaction of the resource update
              (is (= "W/\"1\"" (get headers "ETag"))))

            (testing "Location header"
              (is (= "http://localhost:8080/Patient/0/_history/1"
                     (get headers "Location"))))

            (testing "Contains the resource as body"
              (is (= resource body)))))

        (testing "with return=minimal Prefer header"
          (let [context
                (assoc context
                       :blaze.preference/return :blaze.preference.return/minimal)
                {:keys [body]}
                @(build-response context nil nil resource-handle)]

            (testing "Contains no body"
              (is (nil? body)))))

        (testing "with return=representation Prefer header"
          (let [context
                (assoc context
                       :blaze.preference/return :blaze.preference.return/representation)
                {:keys [body]}
                @(build-response context nil nil resource-handle)]

            (testing "Contains the resource as body"
              (is (= resource body)))))

        (testing "with return=OperationOutcome Prefer header"
          (let [context
                (assoc context
                       :blaze.preference/return :blaze.preference.return/OperationOutcome)
                {:keys [body]}
                @(build-response context nil nil resource-handle)]

            (testing "Contains the OperationOutcome as body"
              (is (= :fhir/OperationOutcome (:fhir/type body)))))))

      (testing "updated"
        (testing "with no Prefer header"
          (let [{:keys [status headers body]}
                @(build-response context nil resource-handle resource-handle)]

            (testing "Returns 200"
              (is (= 200 status)))

            (testing "Transaction time in Last-Modified header"
              (is (= "Thu, 1 Jan 1970 00:00:00 GMT"
                     (get headers "Last-Modified"))))

            (testing "Version in ETag header"
              ;; 1 is the T of the transaction of the resource update
              (is (= "W/\"1\"" (get headers "ETag"))))

            (testing "Contains the resource as body"
              (is (= resource body)))))

        (testing "with return=minimal Prefer header"
          (let [context
                (assoc context
                       :blaze.preference/return :blaze.preference.return/minimal)
                {:keys [body]}
                @(build-response context nil resource-handle resource-handle)]

            (testing "Contains no body"
              (is (nil? body)))))

        (testing "with return=representation Prefer header"
          (let [context
                (assoc context
                       :blaze.preference/return :blaze.preference.return/representation)
                {:keys [body]}
                @(build-response context nil resource-handle resource-handle)]

            (testing "Contains the resource as body"
              (is (= resource body))))))

      (testing "kept"
        (testing "with no Prefer header"
          (let [{:keys [status headers body]}
                @(build-response context [:keep "Patient" "0" #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F"] nil
                                 resource-handle)]

            (testing "Returns 200"
              (is (= 200 status)))

            (testing "Transaction time in Last-Modified header"
              (is (= "Thu, 1 Jan 1970 00:00:00 GMT"
                     (get headers "Last-Modified"))))

            (testing "Version in ETag header"
              ;; 1 is the T of the transaction of the resource update
              (is (= "W/\"1\"" (get headers "ETag"))))

            (testing "Contains the resource as body"
              (is (= resource body)))))))))
