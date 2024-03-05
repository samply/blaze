(ns blaze.rest-api.middleware.sync-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.rest-api.middleware.sync :refer [wrap-sync]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest wrap-sync-test
  (testing "success"
    (is (= ::x (call (wrap-sync (fn [_] (ac/completed-future ::x))) {}))))

  (testing "error"
    (testing "failed future"
      (given (ba/try-anomaly (call (wrap-sync (fn [_] (ac/completed-future (ba/fault)))) {}))
        ::anom/category := ::anom/fault))

    (testing "exception"
      (given (ba/try-anomaly (call (wrap-sync (fn [_] (throw (Error.)))) {}))
        ::anom/category := ::anom/fault))))
