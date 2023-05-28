(ns blaze.db.kv.rocksdb.metrics-test
  (:require
    [blaze.db.kv.rocksdb.metrics :refer [stats-collector block-cache-collector]]
    [blaze.metrics.core :as metrics]
    [blaze.metrics.core-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [org.rocksdb LRUCache RocksDB Statistics]))


(set! *warn-on-reflection* true)
(st/instrument)


(test/use-fixtures :each tu/fixture)


(RocksDB/loadLibrary)


(deftest stats-collector-test
  (let [collector (stats-collector [["foo" (Statistics.)]])
        metrics (metrics/collect collector)]

    (testing "the metrics names are"
      (is (= ["blaze_rocksdb_block_cache_data_miss"
              "blaze_rocksdb_block_cache_data_hit"
              "blaze_rocksdb_block_cache_data_add"
              "blaze_rocksdb_block_cache_data_insert_bytes"
              "blaze_rocksdb_block_cache_index_miss"
              "blaze_rocksdb_block_cache_index_hit"
              "blaze_rocksdb_block_cache_index_add"
              "blaze_rocksdb_block_cache_index_insert_bytes"
              "blaze_rocksdb_block_cache_index_evict_bytes"
              "blaze_rocksdb_block_cache_filter_miss"
              "blaze_rocksdb_block_cache_filter_hit"
              "blaze_rocksdb_block_cache_filter_add"
              "blaze_rocksdb_block_cache_filter_insert_bytes"
              "blaze_rocksdb_block_cache_filter_evict_bytes"
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
              "blaze_rocksdb_file_closes"
              "blaze_rocksdb_file_errors"
              "blaze_rocksdb_stall_seconds"
              "blaze_rocksdb_bloom_filter_useful"
              "blaze_rocksdb_bloom_filter_full_positive"
              "blaze_rocksdb_bloom_filter_full_true_positive"
              "blaze_rocksdb_blocks_compressed"
              "blaze_rocksdb_blocks_decompressed"
              "blaze_rocksdb_blocks_not_compressed"
              "blaze_rocksdb_iterators_created"
              "blaze_rocksdb_wal_syncs"
              "blaze_rocksdb_wal_bytes"
              "blaze_rocksdb_write_timeout"
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
  (let [collector (block-cache-collector (LRUCache. 100))
        metrics (metrics/collect collector)]

    (testing "the metrics names are"
      (is (= ["blaze_rocksdb_block_cache_usage_bytes"
              "blaze_rocksdb_block_cache_pinned_usage_bytes"]
             (mapv :name metrics))))

    (testing "every metric is of type gauge"
      (is (every? (comp #{:gauge} :type) metrics)))

    (testing "every metric has the value 0.0"
      (is (every? (comp #{0.0} :value first :samples) metrics)))))
