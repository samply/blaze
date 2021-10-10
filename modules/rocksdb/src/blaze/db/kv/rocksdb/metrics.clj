(ns blaze.db.kv.rocksdb.metrics
  (:import
    [io.prometheus.client Collector CounterMetricFamily]
    [org.rocksdb Statistics TickerType]))


(set! *warn-on-reflection* true)


(defn- add-metric! [^CounterMetricFamily family name stats ticker-type]
  (.addMetric family [name] (.getTickerCount ^Statistics stats ticker-type)))


(defn- add-metrics! [family stats ticker-type]
  (doseq [[name stats] stats]
    (add-metric! family name stats ticker-type))
  family)


(defn- block-cache-data-miss-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_block_cache_data_miss_total"
        "Returns the number of times cache miss when accessing data block from block cache."
        ["name"])
      (add-metrics! stats TickerType/BLOCK_CACHE_DATA_MISS)))


(defn- block-cache-data-hit-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_block_cache_data_hit_total"
        "Returns the number of times cache hit when accessing data block from block cache."
        ["name"])
      (add-metrics! stats TickerType/BLOCK_CACHE_DATA_HIT)))


(defn- block-cache-data-insert-bytes-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_block_cache_data_insert_bytes_total"
        "Returns the number of bytes of data blocks inserted into block cache."
        ["name"])
      (add-metrics! stats TickerType/BLOCK_CACHE_DATA_BYTES_INSERT)))


(defn- block-cache-index-miss-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_block_cache_index_miss_total"
        "Returns the number of times cache miss when accessing index block from block cache."
        ["name"])
      (add-metrics! stats TickerType/BLOCK_CACHE_INDEX_MISS)))


(defn- block-cache-index-hit-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_block_cache_index_hit_total"
        "Returns the number of times cache hit when accessing index block from block cache."
        ["name"])
      (add-metrics! stats TickerType/BLOCK_CACHE_INDEX_HIT)))


(defn- block-cache-index-insert-bytes-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_block_cache_index_insert_bytes_total"
        "Returns the number of bytes of index blocks inserted into block cache."
        ["name"])
      (add-metrics! stats TickerType/BLOCK_CACHE_INDEX_BYTES_INSERT)))


(defn- block-cache-filter-miss-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_block_cache_filter_miss_total"
        "Returns the number of times cache miss when accessing filter block from block cache."
        ["name"])
      (add-metrics! stats TickerType/BLOCK_CACHE_FILTER_MISS)))


(defn- block-cache-filter-hit-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_block_cache_filter_hit_total"
        "Returns the number of times cache hit when accessing filter block from block cache."
        ["name"])
      (add-metrics! stats TickerType/BLOCK_CACHE_FILTER_HIT)))


(defn- block-cache-filter-insert-bytes-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_block_cache_filter_insert_bytes_total"
        "Returns the number of bytes of filter blocks inserted into block cache."
        ["name"])
      (add-metrics! stats TickerType/BLOCK_CACHE_FILTER_BYTES_INSERT)))


(defn- memtable-hit-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_memtable_hit_total"
        "Returns the number of memtable hits."
        ["name"])
      (add-metrics! stats TickerType/MEMTABLE_HIT)))


(defn- memtable-miss-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_memtable_miss_total"
        "Returns the number of memtable misses."
        ["name"])
      (add-metrics! stats TickerType/MEMTABLE_MISS)))


(defn- get-hit-l0-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_get_hit_l0_total"
        "Returns the number of Get() queries served by L0."
        ["name"])
      (add-metrics! stats TickerType/GET_HIT_L0)))


(defn- get-hit-l1-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_get_hit_l1_total"
        "Returns the number of Get() queries served by L1."
        ["name"])
      (add-metrics! stats TickerType/GET_HIT_L1)))


(defn- get-hit-l2-and-up-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_get_hit_l2_and_up_total"
        "Returns the number of Get() queries served by L2 and up."
        ["name"])
      (add-metrics! stats TickerType/GET_HIT_L2_AND_UP)))


(defn- keys-read-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_keys_read_total"
        "Returns the number of keys read."
        ["name"])
      (add-metrics! stats TickerType/NUMBER_KEYS_READ)))


(defn- keys-written-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_keys_written_total"
        "Returns the number of keys written."
        ["name"])
      (add-metrics! stats TickerType/NUMBER_KEYS_WRITTEN)))


(defn- keys-updated-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_keys_updated_total"
        "Returns the number of keys updated."
        ["name"])
      (add-metrics! stats TickerType/NUMBER_KEYS_UPDATED)))


(defn- seek-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_seek_total"
        "Returns the number of calls to seek."
        ["name"])
      (add-metrics! stats TickerType/NUMBER_DB_SEEK)))


(defn- next-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_next_total"
        "Returns the number of calls to next."
        ["name"])
      (add-metrics! stats TickerType/NUMBER_DB_NEXT)))


(defn- prev-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_prev_total"
        "Returns the number of calls to prev."
        ["name"])
      (add-metrics! stats TickerType/NUMBER_DB_PREV)))


(defn- file-opens-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_file_opens_total"
        "Returns the number of file opens."
        ["name"])
      (add-metrics! stats TickerType/NO_FILE_OPENS)))


(defn- file-closes-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_file_closes_total"
        "Returns the number of file closes."
        ["name"])
      (add-metrics! stats TickerType/NO_FILE_CLOSES)))


(defn- file-errors-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_file_errors_total"
        "Returns the number of file errors."
        ["name"])
      (add-metrics! stats TickerType/NO_FILE_ERRORS)))


(defn- bloom-filter-useful-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_bloom_filter_useful_total"
        "Number of times bloom filter has avoided file reads."
        ["name"])
      (add-metrics! stats TickerType/BLOOM_FILTER_USEFUL)))


(defn- bloom-filter-full-positive-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_bloom_filter_full_positive_total"
        "Number of times bloom FullFilter has not avoided the reads."
        ["name"])
      (add-metrics! stats TickerType/BLOOM_FILTER_FULL_POSITIVE)))


(defn- bloom-filter-full-true-positive-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_bloom_filter_full_true_positive_total"
        "Number of times bloom FullFilter has not avoided the reads and data actually exist."
        ["name"])
      (add-metrics! stats TickerType/BLOOM_FILTER_FULL_TRUE_POSITIVE)))


(defn- blocks-compressed-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_blocks_compressed_total"
        "Number of blocks compressed."
        ["name"])
      (add-metrics! stats TickerType/NUMBER_BLOCK_COMPRESSED)))


(defn- blocks-decompressed-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_blocks_decompressed_total"
        "Number of blocks decompressed."
        ["name"])
      (add-metrics! stats TickerType/NUMBER_BLOCK_DECOMPRESSED)))


(defn- blocks-not-compressed-total [stats]
  (-> (CounterMetricFamily.
        "blaze_rocksdb_blocks_not_compressed_total"
        "Number of blocks not compressed."
        ["name"])
      (add-metrics! stats TickerType/NUMBER_BLOCK_NOT_COMPRESSED)))


(defn stats-collector [stats]
  (proxy [Collector] []
    (collect []
      [(block-cache-data-miss-total stats)
       (block-cache-data-hit-total stats)
       (block-cache-data-insert-bytes-total stats)
       (block-cache-index-miss-total stats)
       (block-cache-index-hit-total stats)
       (block-cache-index-insert-bytes-total stats)
       (block-cache-filter-miss-total stats)
       (block-cache-filter-hit-total stats)
       (block-cache-filter-insert-bytes-total stats)
       (memtable-hit-total stats)
       (memtable-miss-total stats)
       (get-hit-l0-total stats)
       (get-hit-l1-total stats)
       (get-hit-l2-and-up-total stats)
       (keys-read-total stats)
       (keys-written-total stats)
       (keys-updated-total stats)
       (seek-total stats)
       (next-total stats)
       (prev-total stats)
       (file-opens-total stats)
       (file-closes-total stats)
       (file-errors-total stats)
       (bloom-filter-useful-total stats)
       (bloom-filter-full-positive-total stats)
       (bloom-filter-full-true-positive-total stats)
       (blocks-compressed-total stats)
       (blocks-decompressed-total stats)
       (blocks-not-compressed-total stats)])))
