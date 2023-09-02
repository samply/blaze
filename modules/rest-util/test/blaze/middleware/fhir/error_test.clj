(ns blaze.middleware.fhir.error-test
  (:refer-clojure :exclude [error-handler])
  (:require
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [blaze.module.test-util.ring :refer [call]]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(defn- identity-handler [request respond _]
  (respond request))


(defn- error-handler [_ _ raise]
  (raise (Exception.)))


(deftest wrap-error-test
  (testing "without error"
    (is (= {} (call (wrap-error identity-handler) {}))))

  (testing "with error"
    (given (call (wrap-error error-handler) {})
      :status := 500)))
