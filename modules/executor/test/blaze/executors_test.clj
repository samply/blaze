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

(defn- await-execution!
  "Executes a task on `executor` that delivers a promise.

  Returns `::executed` if the task was executed within 10 seconds and
  `::timeout` otherwise."
  [executor]
  (let [executed (promise)]
    (ex/execute! executor #(deliver executed ::executed))
    (deref executed 10000 ::timeout)))

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
  (is (= ::executed (await-execution! (ex/single-thread-executor)))))

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
  (is (= ::executed (await-execution! (ex/cpu-bound-pool "name-%d")))))

(deftest io-pool-test
  (is (= ::executed (await-execution! (ex/io-pool 1 "name-%d")))))

(deftest scheduled-pool-test
  (is (= ::executed (await-execution! (ex/scheduled-pool 1 "name-%d")))))

(deftest single-thread-executor-test
  (is (ex/single-thread-executor))

  (is (= ::executed (await-execution! (ex/single-thread-executor "foo")))))

(deftest pool-size-test
  (testing "cpu-bound-pool"
    (is (= (.availableProcessors (Runtime/getRuntime))
           (ex/pool-size (ex/cpu-bound-pool "name-%d")))))

  (testing "io-pool"
    (are [n] (= n (ex/pool-size (ex/io-pool n "name-%d")))
      1 2 4)))
