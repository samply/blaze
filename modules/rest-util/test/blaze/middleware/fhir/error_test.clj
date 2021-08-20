(ns blaze.middleware.fhir.error-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest wrap-error-test
  (testing "without error"
    (is (= {} @((wrap-error ac/completed-future) {}))))

  (testing "with error"
    (given @((wrap-error (fn [_] (ac/failed-future (Exception.)))) {})
      :status := 500)))
