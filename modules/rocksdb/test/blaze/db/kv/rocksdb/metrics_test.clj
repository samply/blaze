(ns blaze.db.kv.rocksdb.metrics-test
  (:require
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.db.kv.rocksdb.metrics :as metrics]
   [blaze.db.kv.rocksdb.metrics-spec]
   [blaze.metrics.core :as metrics-core]
   [blaze.metrics.core-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]
   [org.rocksdb LRUCache RocksDB Statistics]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(RocksDB/loadLibrary)

(deftest stats-collector-test
  (let [collector (metrics/stats-collector {"foo" (Statistics.)})
        metrics (metrics-core/collect collector)]

    (testing "the metrics names are"
      (is (= ["blaze_rocksdb_block_cache_data_miss"
              "blaze_rocksdb_block_cache_data_hit"
              "blaze_rocksdb_block_cache_data_add"
              "blaze_rocksdb_block_cache_data_insert_bytes"
              "blaze_rocksdb_block_cache_index_miss"
              "blaze_rocksdb_block_cache_index_hit"
              "blaze_rocksdb_block_cache_index_add"
              "blaze_rocksdb_block_cache_index_insert_bytes"
              "blaze_rocksdb_block_cache_filter_miss"
              "blaze_rocksdb_block_cache_filter_hit"
              "blaze_rocksdb_block_cache_filter_add"
              "blaze_rocksdb_block_cache_filter_insert_bytes"
              "blaze_rocksdb_memtable_hit"
              "blaze_rocksdb_memtable_miss"
              "blaze_rocksdb_get_hit_l0"
              "blaze_rocksdb_get_hit_l1"
              "blaze_rocksdb_get_hit_l2_and_up"
              "blaze_rocksdb_keys_read"
              "blaze_rocksdb_keys_written"
              "blaze_rocksdb_keys_updated"
              "blaze_rocksdb_seek"
              "blaze_rocksdb_next"
              "blaze_rocksdb_prev"
              "blaze_rocksdb_file_opens"
              "blaze_rocksdb_file_errors"
              "blaze_rocksdb_stall_seconds"
              "blaze_rocksdb_bloom_filter_useful"
              "blaze_rocksdb_bloom_filter_full_positive"
              "blaze_rocksdb_bloom_filter_full_true_positive"
              "blaze_rocksdb_blocks_compressed"
              "blaze_rocksdb_blocks_decompressed"
              "blaze_rocksdb_blocks_not_compressed"
              "blaze_rocksdb_iterators_created"
              "blaze_rocksdb_iterators_deleted"
              "blaze_rocksdb_wal_syncs"
              "blaze_rocksdb_wal_bytes"
              "blaze_rocksdb_flush_seconds"
              "blaze_rocksdb_compaction_seconds"
              "blaze_rocksdb_compression_seconds"
              "blaze_rocksdb_decompression_seconds"]
             (mapv :name metrics))))

    (testing "every metric is of type counter"
      (is (every? (comp #{:counter} :type) metrics)))

    (testing "every metric has the label value `foo`"
      (is (every? (comp #{["foo"]} :label-values first :samples) metrics)))

    (testing "every metric has the value 0.0"
      (is (every? (comp #{0.0} :value first :samples) metrics)))))

(deftest block-cache-collector-test
  (let [collector (metrics/block-cache-collector (LRUCache. 100))
        metrics (metrics-core/collect collector)]

    (testing "the metrics names are"
      (is (= ["blaze_rocksdb_block_cache_usage_bytes"
              "blaze_rocksdb_block_cache_pinned_usage_bytes"]
             (mapv :name metrics))))

    (testing "every metric is of type gauge"
      (is (every? (comp #{:gauge} :type) metrics)))

    (testing "every metric has the value 0.0"
      (is (every? (comp #{0.0} :value first :samples) metrics)))))

(defn- new-temp-dir! []
  (str (Files/createTempDirectory "blaze" (make-array FileAttribute 0))))

(defn- system [dir]
  {::kv/rocksdb
   {:dir dir
    :block-cache (ig/ref ::rocksdb/block-cache)
    :stats (ig/ref ::rocksdb/stats)}
   ::rocksdb/block-cache {}
   ::rocksdb/stats {}})

(deftest table-reader-collector-test
  (testing "no stores"
    (let [collector (metrics/table-reader-collector nil)
          metrics (metrics-core/collect collector)]

      (given metrics
        count := 1
        [0 :type] := :gauge
        [0 :name] := "blaze_rocksdb_table_reader_usage_bytes"
        [0 :samples count] := 0)))

  (testing "one store"
    (testing "default column family"
      (with-system [{store ::kv/rocksdb} (system (new-temp-dir!))]
        (let [collector (metrics/table-reader-collector {"foo" store})
              metrics (metrics-core/collect collector)]

          (given metrics
            count := 1
            [0 :type] := :gauge
            [0 :name] := "blaze_rocksdb_table_reader_usage_bytes"
            [0 :samples count] := 1
            [0 :samples 0 :label-names] := ["name" "column_family"]
            [0 :samples 0 :label-values] := ["foo" "default"]
            [0 :samples 0 :value] := 0.0))))

    (testing "two custom column families"
      (with-system [{store ::kv/rocksdb} (assoc-in (system (new-temp-dir!)) [::kv/rocksdb :column-families] {:a nil :b nil})]
        (let [collector (metrics/table-reader-collector {"foo" store})
              metrics (metrics-core/collect collector)]

          (given metrics
            count := 1
            [0 :type] := :gauge
            [0 :name] := "blaze_rocksdb_table_reader_usage_bytes"
            [0 :samples count] := 3
            [0 :samples 0 :label-names] := ["name" "column_family"]
            [0 :samples 0 :label-values] := ["foo" "default"]
            [0 :samples 0 :value] := 0.0
            [0 :samples 1 :label-names] := ["name" "column_family"]
            [0 :samples 1 :label-values] := ["foo" "a"]
            [0 :samples 1 :value] := 0.0
            [0 :samples 2 :label-names] := ["name" "column_family"]
            [0 :samples 2 :label-values] := ["foo" "b"]
            [0 :samples 2 :value] := 0.0))))))
