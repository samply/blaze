(ns blaze.rest-api.middleware.log-test
  (:require
   [blaze.module.test-util.ring :refer [call]]
   [blaze.rest-api.middleware.log :refer [wrap-log]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- handler [_ respond _]
  (respond ::x))

(deftest wrap-log-test
  (testing "without query string"
    (is (= ::x (call (wrap-log handler) {:request-method :get :uri "/foo"}))))

  (testing "with query string"
    (is (= ::x (call (wrap-log handler) {:request-method :get :uri "/foo" :query-string "bar"})))))
