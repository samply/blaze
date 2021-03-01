(ns blaze.db.cache-collector-test
  (:require
    [blaze.db.cache-collector :as cc]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine]
    [io.prometheus.client Collector]
    [java.util.function Function]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest cache-collector-test
  (let [cache (-> (Caffeine/newBuilder) (.recordStats) (.build))
        ^Collector collector (cc/cache-collector {"name-135224" cache})]

    (testing "all zero on fresh cache"
      (given (.collect collector)
        [0 #(.-name %)] := "blaze_db_cache_hits"
        [0 #(.-samples %) 0 #(.-value %)] := 0.0
        [1 #(.-name %)] := "blaze_db_cache_loads"
        [1 #(.-samples %) 0 #(.-value %)] := 0.0
        [2 #(.-name %)] := "blaze_db_cache_load_failures"
        [2 #(.-samples %) 0 #(.-value %)] := 0.0
        [3 #(.-name %)] := "blaze_db_cache_load_seconds"
        [3 #(.-samples %) 0 #(.-value %)] := 0.0
        [4 #(.-name %)] := "blaze_db_cache_evictions"
        [4 #(.-samples %) 0 #(.-value %)] := 0.0))

    (testing "one load"
      (.get cache 1 (reify Function (apply [_ key] key)))
      (Thread/sleep 100)

      (given (.collect collector)
        [0 #(.-name %)] := "blaze_db_cache_hits"
        [0 #(.-samples %) 0 #(.-value %)] := 0.0
        [1 #(.-name %)] := "blaze_db_cache_loads"
        [1 #(.-samples %) 0 #(.-value %)] := 1.0))

    (testing "one loads and one hit"
      (.get cache 1 (reify Function (apply [_ key] key)))
      (Thread/sleep 100)

      (given (.collect collector)
        [0 #(.-name %)] := "blaze_db_cache_hits"
        [0 #(.-samples %) 0 #(.-value %)] := 1.0
        [1 #(.-name %)] := "blaze_db_cache_loads"
        [1 #(.-samples %) 0 #(.-value %)] := 1.0))))
