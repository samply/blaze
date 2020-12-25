(ns blaze.fhir.response.create-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.fhir.response.create :refer [build-created-response]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-level :trace (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(def router
  (reitit/router
    [["/Patient/{id}/_history/{vid}" {:name :Patient/versioned-instance}]]
    {:syntax :bracket}))


(deftest build-created-response-test
  (with-open [node (mem-node-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])]
    (let [db (d/db node)
          resource @(d/pull db (d/resource-handle db "Patient" "0"))]

      (testing "with no Prefer header"
        (let [{:keys [status headers body]}
              @(build-created-response router nil db "Patient" "0")]

          (testing "Returns 201"
            (is (= 201 status)))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource update
            (is (= "W/\"1\"" (get headers "ETag"))))

          (testing "Location header"
            (is (= "/Patient/0/_history/1" (get headers "Location"))))

          (testing "Contains the resource as body"
            (is (= resource body)))))

      (testing "with return=minimal Prefer header"
        (let [{:keys [body]}
              (build-created-response router "minimal" db "Patient" "0")]

          (testing "Contains no body"
            (is (nil? body)))))


      (testing "with return=representation Prefer header"
        (let [{:keys [body]}
              @(build-created-response router "representation" db "Patient" "0")]

          (testing "Contains the resource as body"
            (is (= resource body))))))))
