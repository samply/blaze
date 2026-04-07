(ns blaze.openid-client.token-provider.impl-test
  (:require
   [blaze.openid-client.token-provider.impl :as impl]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [java-time.api :as time]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest should-refresh?-test
  (testing "nil state"
    (is (true? (impl/should-refresh? nil))))

  (testing "anomaly state"
    (is (true? (impl/should-refresh? {::anom/category ::anom/fault}))))

  (testing "state without expires-at"
    (is (true? (impl/should-refresh? {:token "my-token"}))))

  (testing "state with expires-at within 5 minutes"
    (is (true? (impl/should-refresh? {:token "my-token"
                                      :expires-at (time/plus (time/instant) (time/minutes 4))}))))

  (testing "state with expires-at more than 5 minutes away"
    (is (false? (impl/should-refresh? {:token "my-token"
                                       :expires-at (time/plus (time/instant) (time/minutes 6))})))))

(deftest token-test
  (testing "nil state returns unavailable anomaly"
    (given (impl/token nil)
      ::anom/category := ::anom/unavailable
      ::anom/message := "No token available yet."))

  (testing "anomaly state is returned as-is"
    (given (impl/token {::anom/category ::anom/fault ::anom/message "some error"})
      ::anom/category := ::anom/fault
      ::anom/message := "some error"))

  (testing "valid state returns token"
    (is (= "my-token" (impl/token {:token "my-token"})))))
