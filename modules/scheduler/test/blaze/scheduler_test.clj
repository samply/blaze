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
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest schedule-at-fixed-rate-test
  (with-system [{:blaze/keys [scheduler]} {:blaze/scheduler {}}]
    (let [calls (atom 0)
          called-twice (promise)]
      (sched/schedule-at-fixed-rate
       scheduler
       #(when (= 2 (swap! calls inc)) (deliver called-twice true))
       (time/millis 100) (time/millis 100))

      (testing "the function wasn't called yet, because of the initial delay"
        (is (zero? @calls)))

      (testing "the function is called again after the period elapsed"
        (is (true? (deref called-twice 10000 false)))))))

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
