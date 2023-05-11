(ns blaze.db.cache-collector-test
  (:require
    [blaze.db.cache-collector]
    [blaze.metrics.core :as metrics]
    [blaze.test-util :as tu :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]])
  (:import
    [com.github.benmanes.caffeine.cache Cache Caffeine]
    [java.util.function Function]))


(set! *warn-on-reflection* true)
(st/instrument)


(test/use-fixtures :each tu/fixture)


(def ^Cache cache (-> (Caffeine/newBuilder) (.recordStats) (.build)))


(def system
  {:blaze.db/cache-collector
   {:caches {"name-135224" cache "name-093214" nil}}})


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.db/cache-collector nil})
      :key := :blaze.db/cache-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.db/cache-collector {}})
      :key := :blaze.db/cache-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :caches))))

  (testing "invalid caches"
    (given-thrown (ig/init {:blaze.db/cache-collector {:caches ::invalid}})
      :key := :blaze.db/cache-collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?
      [:explain ::s/problems 0 :val] := ::invalid)))


(deftest cache-collector-test
  (with-system [{collector :blaze.db/cache-collector} system]

    (testing "all zero on fresh cache"
      (given (metrics/collect collector)
        [0 :name] := "blaze_db_cache_hits"
        [0 :type] := :counter
        [0 :samples 0 :value] := 0.0
        [1 :name] := "blaze_db_cache_misses"
        [1 :type] := :counter
        [1 :samples 0 :value] := 0.0
        [2 :name] := "blaze_db_cache_load_successes"
        [2 :type] := :counter
        [2 :samples 0 :value] := 0.0
        [3 :name] := "blaze_db_cache_load_failures"
        [3 :type] := :counter
        [3 :samples 0 :value] := 0.0
        [4 :name] := "blaze_db_cache_load_seconds"
        [4 :type] := :counter
        [4 :samples 0 :value] := 0.0
        [5 :name] := "blaze_db_cache_evictions"
        [5 :type] := :counter
        [5 :samples 0 :value] := 0.0
        [6 :name] := "blaze_db_cache_estimated_size"
        [6 :type] := :gauge
        [6 :samples 0 :value] := 0.0))

    (testing "one load"
      (.get cache 1 (reify Function (apply [_ key] key)))
      (Thread/sleep 100)

      (given (metrics/collect collector)
        [0 :name] := "blaze_db_cache_hits"
        [0 :samples 0 :value] := 0.0
        [1 :name] := "blaze_db_cache_misses"
        [1 :samples 0 :value] := 1.0
        [2 :name] := "blaze_db_cache_load_successes"
        [2 :samples 0 :value] := 1.0
        [3 :name] := "blaze_db_cache_load_failures"
        [3 :samples 0 :value] := 0.0
        [5 :name] := "blaze_db_cache_evictions"
        [5 :samples 0 :value] := 0.0
        [6 :name] := "blaze_db_cache_estimated_size"
        [6 :samples 0 :value] := 1.0))

    (testing "one loads and one hit"
      (.get cache 1 (reify Function (apply [_ key] key)))
      (Thread/sleep 100)

      (given (metrics/collect collector)
        [0 :name] := "blaze_db_cache_hits"
        [0 :samples 0 :value] := 1.0
        [1 :name] := "blaze_db_cache_misses"
        [1 :samples 0 :value] := 1.0
        [2 :name] := "blaze_db_cache_load_successes"
        [2 :samples 0 :value] := 1.0
        [3 :name] := "blaze_db_cache_load_failures"
        [3 :samples 0 :value] := 0.0
        [5 :name] := "blaze_db_cache_evictions"
        [5 :samples 0 :value] := 0.0
        [6 :name] := "blaze_db_cache_estimated_size"
        [6 :samples 0 :value] := 1.0))))
