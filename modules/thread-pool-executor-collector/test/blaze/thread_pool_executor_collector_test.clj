(ns blaze.thread-pool-executor-collector-test
  (:require
    [blaze.test-util :refer [given-thrown with-system]]
    [blaze.thread-pool-executor-collector]
    [blaze.thread-pool-executor-collector.spec :as spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [io.prometheus.client Collector$Type]
    [java.util.concurrent Executors Executor]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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


(def system
  {:blaze/thread-pool-executor-collector
   {:executors {:a (ig/ref ::pool)}}
   ::pool {}})


(defmethod ig/init-key ::pool [_ _] (Executors/newFixedThreadPool 1))


(deftest collector-test
  (with-system [{collector :blaze/thread-pool-executor-collector ::keys [pool]}
                system]

    (testing "fresh pool"
      (given (.collect collector)
        [0 #(.-name %)] := "thread_pool_executor_active_count"
        [0 #(.-type %)] := Collector$Type/GAUGE
        [0 #(.-samples %) 0 #(.-value %)] := 0.0
        [1 #(.-name %)] := "thread_pool_executor_completed_tasks"
        [1 #(.-type %)] := Collector$Type/COUNTER
        [1 #(.-samples %) 0 #(.-value %)] := 0.0
        [2 #(.-name %)] := "thread_pool_executor_core_pool_size"
        [2 #(.-type %)] := Collector$Type/GAUGE
        [2 #(.-samples %) 0 #(.-value %)] := 1.0
        [3 #(.-name %)] := "thread_pool_executor_largest_pool_size"
        [3 #(.-type %)] := Collector$Type/GAUGE
        [3 #(.-samples %) 0 #(.-value %)] := 0.0
        [4 #(.-name %)] := "thread_pool_executor_maximum_pool_size"
        [4 #(.-type %)] := Collector$Type/GAUGE
        [4 #(.-samples %) 0 #(.-value %)] := 1.0
        [5 #(.-name %)] := "thread_pool_executor_pool_size"
        [5 #(.-type %)] := Collector$Type/GAUGE
        [5 #(.-samples %) 0 #(.-value %)] := 0.0
        [6 #(.-name %)] := "thread_pool_executor_queue_size"
        [6 #(.-type %)] := Collector$Type/GAUGE
        [6 #(.-samples %) 0 #(.-value %)] := 0.0))

    (testing "one active thread"
      (.execute ^Executor pool #(Thread/sleep 100))
      (given (.collect collector)
        [0 #(.-name %)] := "thread_pool_executor_active_count"
        [0 #(.-samples %) 0 #(.-value %)] := 1.0
        [1 #(.-name %)] := "thread_pool_executor_completed_tasks"
        [1 #(.-samples %) 0 #(.-value %)] := 0.0))))
