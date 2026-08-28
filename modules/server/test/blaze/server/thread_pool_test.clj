(ns blaze.server.thread-pool-test
  (:require
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.server.thread-pool]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [org.eclipse.jetty.util.thread QueuedThreadPool]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.server/thread-pool nil}
      :key := :blaze.server/thread-pool
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid max threads"
    (given-failed-system {:blaze.server/thread-pool {:max-threads ::invalid}}
      :key := :blaze.server/thread-pool
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.server/max-threads]
      [:cause-data ::s/problems 0 :val] := ::invalid)

    (testing "below the lower bound of 8"
      (given-failed-system {:blaze.server/thread-pool {:max-threads 7}}
        :key := :blaze.server/thread-pool
        :reason := ::ig/build-failed-spec
        [:cause-data ::s/problems 0 :via] := [:blaze.server/max-threads]
        [:cause-data ::s/problems 0 :val] := 7)))

  (testing "there is no upper bound"
    (with-system [{pool :blaze.server/thread-pool}
                  {:blaze.server/thread-pool {:max-threads 1000}}]
      (is (= 1000 (.getMaxThreads ^QueuedThreadPool pool))))))

(deftest thread-pool-test
  (testing "with default max threads"
    (with-system [{pool :blaze.server/thread-pool} {:blaze.server/thread-pool {}}]
      (given pool
        #(.getName ^QueuedThreadPool %) := "http"
        #(.getMinThreads ^QueuedThreadPool %) := 8
        #(.getMaxThreads ^QueuedThreadPool %) := 50
        #(.isRunning ^QueuedThreadPool %) := true)))

  (testing "with custom max threads"
    (with-system [{pool :blaze.server/thread-pool}
                  {:blaze.server/thread-pool {:max-threads 64}}]
      (is (= 64 (.getMaxThreads ^QueuedThreadPool pool)))))

  (testing "it is stopped on halt"
    (let [{pool :blaze.server/thread-pool :as system}
          (ig/init {:blaze.server/thread-pool {}})]
      (is (.isRunning ^QueuedThreadPool pool))
      (ig/halt! system)
      (is (.isStopped ^QueuedThreadPool pool)))))
