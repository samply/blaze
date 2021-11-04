(ns blaze.db.cache-collector-test
  (:require
    [blaze.db.cache-collector]
    [blaze.test-util :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine]
    [io.prometheus.client Collector$Type]
    [java.util.function Function]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def cache (-> (Caffeine/newBuilder) (.recordStats) (.build)))


(def system
  {:blaze.db/cache-collector
   {:caches {"name-135224" cache}}})


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
      (given (.collect collector)
        [0 #(.-name %)] := "blaze_db_cache_hits"
        [0 #(.-type %)] := Collector$Type/COUNTER
        [0 #(.-samples %) 0 #(.-value %)] := 0.0
        [1 #(.-name %)] := "blaze_db_cache_loads"
        [1 #(.-type %)] := Collector$Type/COUNTER
        [1 #(.-samples %) 0 #(.-value %)] := 0.0
        [2 #(.-name %)] := "blaze_db_cache_load_failures"
        [2 #(.-type %)] := Collector$Type/COUNTER
        [2 #(.-samples %) 0 #(.-value %)] := 0.0
        [3 #(.-name %)] := "blaze_db_cache_load_seconds"
        [3 #(.-type %)] := Collector$Type/COUNTER
        [3 #(.-samples %) 0 #(.-value %)] := 0.0
        [4 #(.-name %)] := "blaze_db_cache_evictions"
        [4 #(.-samples %) 0 #(.-value %)] := 0.0
        [4 #(.-type %)] := Collector$Type/COUNTER))

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
