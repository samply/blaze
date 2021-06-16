(ns blaze.fhir.response.create-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.fhir.response.create :refer [build-response]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]]
    {:syntax :bracket}))


(deftest build-response-test
  (with-open [node (mem-node-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])]
    (let [db (d/db node)
          resource-handle (d/resource-handle db "Patient" "0")
          resource @(d/pull db resource-handle)]

      (testing "created"
        (testing "with no Prefer header"
          (let [{:keys [status headers body]}
                @(build-response "http://localhost:8080" router nil db nil
                                 resource-handle)]

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
          (let [{:keys [body]}
                @(build-response "http://localhost:8080" router "minimal" db nil
                                 resource-handle)]

            (testing "Contains no body"
              (is (nil? body)))))

        (testing "with return=representation Prefer header"
          (let [{:keys [body]}
                @(build-response "http://localhost:8080" router "representation"
                                 db nil resource-handle)]

            (testing "Contains the resource as body"
              (is (= resource body))))))

      (testing "updated"
        (testing "with no Prefer header"
          (let [{:keys [status headers body]}
                @(build-response "http://localhost:8080" router nil db
                                 resource-handle resource-handle)]

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
          (let [{:keys [body]}
                @(build-response "http://localhost:8080" router "minimal" db
                                 resource-handle resource-handle)]

            (testing "Contains no body"
              (is (nil? body)))))

        (testing "with return=representation Prefer header"
          (let [{:keys [body]}
                @(build-response "http://localhost:8080" router "representation"
                                 db resource-handle resource-handle)]

            (testing "Contains the resource as body"
              (is (= resource body)))))))))
