(ns blaze.queued-thread-pool-collector-test
  (:require
   [blaze.metrics.core :as metrics]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.queued-thread-pool-collector]
   [blaze.queued-thread-pool-collector.spec :as spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent CountDownLatch]
   [org.eclipse.jetty.util.thread QueuedThreadPool]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/queued-thread-pool-collector nil}
      :key := :blaze/queued-thread-pool-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing thread pool"
    (given-failed-system {:blaze/queued-thread-pool-collector {}}
      :key := :blaze/queued-thread-pool-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :thread-pool))))

  (testing "invalid thread pool"
    (given-failed-system {:blaze/queued-thread-pool-collector {:thread-pool ::invalid}}
      :key := :blaze/queued-thread-pool-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `spec/queued-thread-pool?
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def ^:private config
  {:blaze/queued-thread-pool-collector
   {:thread-pool (ig/ref ::pool)}
   ::pool {}})

(defmethod ig/init-key ::pool [_ _]
  (doto (QueuedThreadPool. 50 8)
    (.setName "test")
    (.start)))

(defmethod ig/halt-key! ::pool [_ pool]
  (.stop ^QueuedThreadPool pool))

(deftest collector-test
  (with-system [{collector :blaze/queued-thread-pool-collector ::keys [pool]}
                config]

    (testing "idle pool"
      (given (metrics/collect collector)
        [0 :name] := "queued_thread_pool_threads"
        [0 :type] := :gauge
        [0 :samples 0 :label-names] := ["name"]
        [0 :samples 0 :label-values] := ["test"]
        [0 :samples 0 :value] := 8.0
        [1 :name] := "queued_thread_pool_idle_threads"
        [1 :type] := :gauge
        [1 :samples 0 :value] := 8.0
        [2 :name] := "queued_thread_pool_busy_threads"
        [2 :type] := :gauge
        [2 :samples 0 :value] := 0.0
        [3 :name] := "queued_thread_pool_queue_size"
        [3 :type] := :gauge
        [3 :samples 0 :value] := 0.0
        [4 :name] := "queued_thread_pool_utilization_rate"
        [4 :type] := :gauge
        [4 :samples 0 :value] := 0.0
        [5 :name] := "queued_thread_pool_low_on_threads"
        [5 :type] := :gauge
        [5 :samples 0 :value] := 0.0))

    (testing "one busy thread"
      (let [started (CountDownLatch. 1)
            finish (CountDownLatch. 1)]
        (.execute ^QueuedThreadPool pool #(do (.countDown started) (.await finish)))
        (.await started)
        (try
          (given (metrics/collect collector)
            [1 :name] := "queued_thread_pool_idle_threads"
            [1 :samples 0 :value] := 7.0
            [2 :name] := "queued_thread_pool_busy_threads"
            [2 :samples 0 :value] := 1.0
            [4 :name] := "queued_thread_pool_utilization_rate"
            [4 :samples 0 :value] := 0.02)
          (finally
            (.countDown finish)))))))
