(ns blaze.thread-pool-executor-collector-test
  (:require
    [blaze.executors :as ex]
    [blaze.metrics.core :as metrics]
    [blaze.module.test-util :refer [with-system]]
    [blaze.test-util :as tu :refer [given-thrown]]
    [blaze.thread-pool-executor-collector]
    [blaze.thread-pool-executor-collector.spec :as spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent Executors]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze/thread-pool-executor-collector nil})
      :key := :blaze/thread-pool-executor-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "nil executors"
    (given-thrown (ig/init {:blaze/thread-pool-executor-collector {:executors nil}})
      :key := :blaze/thread-pool-executor-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid executor key"
    (given-thrown (ig/init {:blaze/thread-pool-executor-collector {:executors {"a" nil}}})
      :key := :blaze/thread-pool-executor-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `keyword?
      [:explain ::s/problems 0 :val] := "a"))

  (testing "invalid executor"
    (given-thrown (ig/init {:blaze/thread-pool-executor-collector {:executors {:a nil}}})
      :key := :blaze/thread-pool-executor-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `spec/thread-pool-executor?
      [:explain ::s/problems 0 :val] := nil)))


(def config
  {:blaze/thread-pool-executor-collector
   {:executors {:a (ig/ref ::pool)}}
   ::pool {}})


(defmethod ig/init-key ::pool [_ _]
  (Executors/newFixedThreadPool 1))


(deftest collector-test
  (with-system [{collector :blaze/thread-pool-executor-collector ::keys [pool]}
                config]

    (testing "fresh pool"
      (given (metrics/collect collector)
        [0 :name] := "thread_pool_executor_active_count"
        [0 :type] := :gauge
        [0 :samples 0 :value] := 0.0
        [1 :name] := "thread_pool_executor_completed_tasks"
        [1 :type] := :counter
        [1 :samples 0 :name] := "thread_pool_executor_completed_tasks_total"
        [1 :samples 0 :value] := 0.0
        [2 :name] := "thread_pool_executor_core_pool_size"
        [2 :type] := :gauge
        [2 :samples 0 :value] := 1.0
        [3 :name] := "thread_pool_executor_largest_pool_size"
        [3 :type] := :gauge
        [3 :samples 0 :value] := 0.0
        [4 :name] := "thread_pool_executor_maximum_pool_size"
        [4 :type] := :gauge
        [4 :samples 0 :value] := 1.0
        [5 :name] := "thread_pool_executor_pool_size"
        [5 :type] := :gauge
        [5 :samples 0 :value] := 0.0
        [6 :name] := "thread_pool_executor_queue_size"
        [6 :type] := :gauge
        [6 :samples 0 :value] := 0.0))

    (testing "one active thread"
      (ex/execute! pool #(Thread/sleep 1000))
      (given (metrics/collect collector)
        [0 :name] := "thread_pool_executor_active_count"
        [0 :samples 0 :value] := 1.0
        [1 :name] := "thread_pool_executor_completed_tasks"
        [1 :samples 0 :value] := 0.0))))
