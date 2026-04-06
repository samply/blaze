(ns blaze.jvm-metrics-logger-test
  (:require
   [blaze.jvm-metrics-logger :as jml]
   [blaze.module-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.scheduler.spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  {:blaze/jvm-metrics-logger
   {:scheduler (ig/ref :blaze/scheduler)
    :interval (time/seconds 5)
    :warn-factor 1
    :warn-threshold 80}

   :blaze/scheduler {}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/jvm-metrics-logger nil}
      :key := :blaze/jvm-metrics-logger
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing scheduler"
    (given-failed-system {:blaze/jvm-metrics-logger {}}
      :key := :blaze/jvm-metrics-logger
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))))

  (testing "invalid scheduler"
    (given-failed-system (assoc-in config [:blaze/jvm-metrics-logger :scheduler] ::invalid)
      :key := :blaze/jvm-metrics-logger
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/scheduler]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid warn-threshold"
    (doseq [threshold [::invalid 0 100]]
      (given-failed-system (assoc-in config [:blaze/jvm-metrics-logger :warn-threshold] threshold)
        :key := :blaze/jvm-metrics-logger
        :reason := ::ig/build-failed-spec
        [:cause-data ::s/problems 0 :via] := [::jml/warn-threshold]
        [:cause-data ::s/problems 0 :val] := threshold)))

  (testing "success"
    (with-system [system config]
      (is (some? (get system :blaze/jvm-metrics-logger))))))

(deftest scheduled-debug-task-test
  (testing "debug task fires and logs without error when heap is below threshold"
    (with-system [_ {:blaze/jvm-metrics-logger
                     {:scheduler (ig/ref :blaze/scheduler)
                      :interval (time/millis 50)
                      :warn-factor 1
                      :warn-threshold 99}
                     :blaze/scheduler {}}]
      (Thread/sleep 200)
      (is true))))

(deftest scheduled-warn-task-test
  (testing "warn task fires and logs without error when heap is above threshold"
    (with-system [_ {:blaze/jvm-metrics-logger
                     {:scheduler (ig/ref :blaze/scheduler)
                      :interval (time/millis 50)
                      :warn-factor 1
                      :warn-threshold 1}
                     :blaze/scheduler {}}]
      (Thread/sleep 200)
      (is true))))
