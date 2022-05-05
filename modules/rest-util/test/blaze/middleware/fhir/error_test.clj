(ns blaze.middleware.fhir.error-test
  (:refer-clojure :exclude [error-handler])
  (:require
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [blaze.test-util.ring :refer [call]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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
