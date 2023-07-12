(ns blaze.scheduler-test
  (:require
    [blaze.executors :as ex]
    [blaze.module.test-util :refer [with-system]]
    [blaze.scheduler :as sched]
    [blaze.scheduler-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [java-time.api :as time]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(deftest schedule-at-fixed-rate-test
  (with-system [{:blaze/keys [scheduler]} {:blaze/scheduler {}}]
    (let [state (atom 0)]
      (sched/schedule-at-fixed-rate scheduler #(swap! state inc)
                                    (time/millis 100) (time/millis 100))

      (testing "the function wasn't called yet"
        (is (zero? @state)))

      (Thread/sleep 120)

      (testing "the function was called once"
        (is (= 1 @state)))

      (Thread/sleep 100)

      (testing "the function was called twice"
        (is (= 2 @state))))))


(deftest cancel-test
  (with-system [{:blaze/keys [scheduler]} {:blaze/scheduler {}}]
    (let [future (sched/schedule-at-fixed-rate scheduler identity
                                               (time/millis 100)
                                               (time/millis 100))]

      (is (sched/cancel future false)))))


(deftest shutdown-timeout-test
  (let [{:blaze/keys [scheduler] :as system} (ig/init {:blaze/scheduler {}})]

    ;; will produce a timeout, because the function runs 11 seconds
    (sched/schedule-at-fixed-rate scheduler #(Thread/sleep 11000)
                                  (time/millis 0) (time/millis 100))

    ;; ensure that the function is called before the scheduler is halted
    (Thread/sleep 100)

    (ig/halt! system)

    ;; the scheduler is shut down
    (is (ex/shutdown? scheduler))

    ;; but it isn't terminated yet
    (is (not (ex/terminated? scheduler)))))
