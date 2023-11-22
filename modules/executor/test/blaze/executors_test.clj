(ns blaze.executors-test
  (:require
   [blaze.executors :as ex]
   [blaze.executors-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]])
  (:import
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest executor-test
  (are [x] (false? (ex/executor? x))
    nil
    1
    "")

  (is (true? (ex/executor? (ex/single-thread-executor)))))

(deftest executor-service-test
  (are [x] (false? (ex/executor-service? x))
    nil
    1
    "")

  (is (true? (ex/executor-service? (ex/single-thread-executor)))))

(deftest execute-test
  (let [state (atom 0)]
    (ex/execute! (ex/single-thread-executor) #(reset! state 1))
    (Thread/sleep 10)
    (is (= 1 @state))))

(deftest shutdown-test
  (testing "a newly created executor isn't shut down"
    (is (false? (ex/shutdown? (ex/single-thread-executor)))))

  (let [executor (ex/single-thread-executor)]
    (ex/shutdown! executor)
    (is (true? (ex/shutdown? executor)))))

(deftest terminated-test
  (testing "a newly created executor isn't terminated"
    (is (false? (ex/terminated? (ex/single-thread-executor)))))

  (let [executor (ex/single-thread-executor)]
    (ex/shutdown! executor)
    (is (true? (ex/terminated? executor)))))

(deftest await-termination-test
  (let [executor (ex/single-thread-executor)]
    (ex/shutdown! executor)
    (is (true? (ex/await-termination executor 1 TimeUnit/SECONDS)))))

(deftest cpu-bound-pool-test
  (let [pool (ex/cpu-bound-pool "name-%d")
        state (atom 0)]
    (ex/execute! pool #(reset! state 1))
    (Thread/sleep 10)
    (is (= 1 @state))))

(deftest io-pool-test
  (let [pool (ex/io-pool 1 "name-%d")
        state (atom 0)]
    (ex/execute! pool #(reset! state 1))
    (Thread/sleep 10)
    (is (= 1 @state))))

(deftest single-thread-executor-test
  (is (ex/single-thread-executor))

  (let [executor (ex/single-thread-executor "foo")
        state (atom 0)]
    (ex/execute! executor #(reset! state 1))
    (Thread/sleep 10)
    (is (= 1 @state))))
