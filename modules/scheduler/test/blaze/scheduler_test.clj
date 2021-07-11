(ns blaze.scheduler-test
  (:require
    [blaze.scheduler :as sched]
    [blaze.scheduler-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]
    [integrant.core :as ig]
    [java-time :as time]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn init-scheduler []
  (-> {:blaze/scheduler {}} ig/init :blaze/scheduler))


(defn halt-scheduler! [scheduler]
  (ig/halt-key! :blaze/scheduler scheduler))


(deftest schedule-at-fixed-rate-test
  (let [scheduler (init-scheduler)
        state (atom 0)]
    (sched/schedule-at-fixed-rate scheduler #(swap! state inc)
                                  (time/millis 100) (time/millis 100))
    (is (zero? @state))
    (Thread/sleep 120)
    (is (= 1 @state))
    (Thread/sleep 100)
    (is (= 2 @state))
    (halt-scheduler! scheduler)))
