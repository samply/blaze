(ns blaze.interaction.delete-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.delete :refer [handler]]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn handler-with [txs]
  (handler (mem-node-with txs)))


(deftest handler-test
  (testing "Returns Not Found on non-existing resource"
    (let [{:keys [status body]}
          @((handler-with [])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Returns No Content on successful deletion"
    (let [{:keys [status headers body]}
          @((handler-with [[[:put {:resourceType "Patient" :id "0"}]]])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 204 status))

      (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 2 is the T of the transaction of the resource update
        (is (= "W/\"2\"" (get headers "ETag"))))

      (is (nil? body))))


  (testing "Returns No Content on already deleted resource"
    (let [{:keys [status headers body]}
          @((handler-with
              [[[:put {:resourceType "Patient" :id "0"}]]
               [[:delete "Patient" "0"]]])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 204 status))

      (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 2 is the T of the transaction of the resource update
        (is (= "W/\"2\"" (get headers "ETag"))))

      (is (nil? body)))))
