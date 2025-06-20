(ns blaze.cache-collector-test
  (:require
   [blaze.cache-collector]
   [blaze.cache-collector.spec]
   [blaze.metrics.core :as metrics]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]])
  (:import
   [com.github.benmanes.caffeine.cache AsyncCache Cache Caffeine]
   [java.util.function Function]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^Cache cache (-> (Caffeine/newBuilder) (.recordStats) (.build)))
(def ^AsyncCache async-cache (-> (Caffeine/newBuilder) (.recordStats) (.buildAsync)))

(def config
  {:blaze/cache-collector
   {:caches
    {"name-135224" cache
     "name-145135" async-cache
     "name-093214" nil}}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/cache-collector nil}
      :key := :blaze/cache-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze/cache-collector {}}
      :key := :blaze/cache-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :caches))))

  (testing "invalid caches"
    (given-failed-system (assoc-in config [:blaze/cache-collector :caches] ::invalid)
      :key := :blaze/cache-collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.cache-collector/caches]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest cache-collector-test
  (with-system [{collector :blaze/cache-collector} config]

    (testing "all zero on fresh cache"
      (given (metrics/collect collector)
        [0 :name] := "blaze_cache_hits"
        [0 :type] := :counter
        [0 :samples count] := 2
        [0 :samples 0 :value] := 0.0
        [0 :samples 0 :label-values] := ["name-135224"]
        [0 :samples 1 :value] := 0.0
        [0 :samples 1 :label-values] := ["name-145135"]
        [1 :name] := "blaze_cache_misses"
        [1 :type] := :counter
        [1 :samples count] := 2
        [1 :samples 0 :value] := 0.0
        [1 :samples 1 :value] := 0.0
        [2 :name] := "blaze_cache_load_successes"
        [2 :type] := :counter
        [2 :samples count] := 2
        [2 :samples 0 :value] := 0.0
        [2 :samples 1 :value] := 0.0
        [3 :name] := "blaze_cache_load_failures"
        [3 :type] := :counter
        [3 :samples count] := 2
        [3 :samples 0 :value] := 0.0
        [3 :samples 1 :value] := 0.0
        [4 :name] := "blaze_cache_load_seconds"
        [4 :type] := :counter
        [4 :samples count] := 2
        [4 :samples 0 :value] := 0.0
        [4 :samples 1 :value] := 0.0
        [5 :name] := "blaze_cache_evictions"
        [5 :type] := :counter
        [5 :samples count] := 2
        [5 :samples 0 :value] := 0.0
        [5 :samples 1 :value] := 0.0
        [6 :name] := "blaze_cache_estimated_size"
        [6 :type] := :gauge
        [6 :samples count] := 2
        [6 :samples 0 :value] := 0.0
        [6 :samples 1 :value] := 0.0))

    (testing "one load"
      (.get cache "1" identity)
      (.get async-cache "1" ^Function identity)
      (Thread/sleep 100)

      (given (metrics/collect collector)
        [0 :name] := "blaze_cache_hits"
        [0 :samples 0 :value] := 0.0
        [0 :samples 1 :value] := 0.0
        [1 :name] := "blaze_cache_misses"
        [1 :samples 0 :value] := 1.0
        [1 :samples 1 :value] := 1.0
        [2 :name] := "blaze_cache_load_successes"
        [2 :samples 0 :value] := 1.0
        [2 :samples 1 :value] := 1.0
        [3 :name] := "blaze_cache_load_failures"
        [3 :samples 0 :value] := 0.0
        [3 :samples 1 :value] := 0.0
        [5 :name] := "blaze_cache_evictions"
        [5 :samples 0 :value] := 0.0
        [5 :samples 1 :value] := 0.0
        [6 :name] := "blaze_cache_estimated_size"
        [6 :samples 0 :value] := 1.0
        [6 :samples 1 :value] := 1.0))

    (testing "one loads and one hit"
      (.get cache "1" identity)
      (.get async-cache "1" ^Function identity)
      (Thread/sleep 100)

      (given (metrics/collect collector)
        [0 :name] := "blaze_cache_hits"
        [0 :samples 0 :value] := 1.0
        [0 :samples 1 :value] := 1.0
        [1 :name] := "blaze_cache_misses"
        [1 :samples 0 :value] := 1.0
        [1 :samples 1 :value] := 1.0
        [2 :name] := "blaze_cache_load_successes"
        [2 :samples 0 :value] := 1.0
        [2 :samples 1 :value] := 1.0
        [3 :name] := "blaze_cache_load_failures"
        [3 :samples 0 :value] := 0.0
        [3 :samples 1 :value] := 0.0
        [5 :name] := "blaze_cache_evictions"
        [5 :samples 0 :value] := 0.0
        [5 :samples 1 :value] := 0.0
        [6 :name] := "blaze_cache_estimated_size"
        [6 :samples 0 :value] := 1.0
        [6 :samples 1 :value] := 1.0))))
