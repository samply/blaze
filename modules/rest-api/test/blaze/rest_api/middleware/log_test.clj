(ns blaze.rest-api.middleware.log-test
  (:require
    [blaze.rest-api.middleware.log :refer [wrap-log]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [taoensso.timbre :as log]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest wrap-log-test
  (testing "without query string"
    (is (= :x ((wrap-log (fn [_] :x)) {:request-method :get :uri "/foo"}))))

  (testing "with query string"
    (is (= :x ((wrap-log (fn [_] :x)) {:request-method :get :uri "/foo" :query-string "bar"})))))
