(ns blaze.middleware.fhir.error-test
  (:refer-clojure :exclude [error-handler])
  (:require
   [blaze.anomaly :as ba]
   [blaze.middleware.fhir.error :refer [wrap-error wrap-json-error]]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- identity-handler [request respond _]
  (respond request))

(defn- error-handler
  ([]
   (error-handler (Exception.)))
  ([error]
   (fn [_ _ raise]
     (raise error))))

(deftest wrap-error-test
  (testing "without error"
    (is (= {} (call (wrap-error identity-handler) {}))))

  (testing "with error"
    (testing "plain exception"
      (given (call (wrap-error (error-handler)) {})
        :status := 500))

    (testing "anomaly with status"
      (given (call (wrap-error (error-handler (ba/fault "" :http/status 503))) {})
        :status := 503))))

(deftest wrap-json-error-test
  (testing "without error"
    (is (= {} (call (wrap-json-error identity-handler) {}))))

  (testing "with error"
    (testing "plain exception"
      (given (call (wrap-json-error (error-handler)) {})
        :status := 500))

    (testing "anomaly with status"
      (given (call (wrap-json-error (error-handler (ba/fault "" :http/status 503))) {})
        :status := 503))))
