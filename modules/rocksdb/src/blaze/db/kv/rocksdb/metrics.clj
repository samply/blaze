(ns blaze.db.kv.rocksdb.metrics
  (:require
    [blaze.metrics.core :as metrics])
  (:import
    [org.rocksdb Statistics TickerType]))


(set! *warn-on-reflection* true)


(defn- sample-xf [ticker-type]
  (map
    (fn [[name stats]]
      {:label-values [name]
       :value (.getTickerCount ^Statistics stats ticker-type)})))


(defn- samples [ticker-type stats]
  (into [] (sample-xf ticker-type) stats))


(defn- counter-metric [name help ticker-type]
  (fn [stats]
    (metrics/counter-metric name help ["name"] (samples ticker-type stats))))


(def ^:private block-cache-data-miss-total
  (counter-metric
    "blaze_rocksdb_block_cache_data_miss_total"
    "Returns the number of times cache miss when accessing data block from block cache."
    TickerType/BLOCK_CACHE_DATA_MISS))


(def ^:private block-cache-data-hit-total
  (counter-metric
    "blaze_rocksdb_block_cache_data_hit_total"
    "Returns the number of times cache hit when accessing data block from block cache."
    TickerType/BLOCK_CACHE_DATA_HIT))


(def ^:private block-cache-data-add-total
  (counter-metric
    "blaze_rocksdb_block_cache_data_add_total"
    "Returns the number of data blocks added to block cache."
    TickerType/BLOCK_CACHE_DATA_ADD))


(def ^:private block-cache-data-insert-bytes-total
  (counter-metric
    "blaze_rocksdb_block_cache_data_insert_bytes_total"
    "Returns the number of bytes of data blocks inserted into block cache."
    TickerType/BLOCK_CACHE_DATA_BYTES_INSERT))


(def ^:private block-cache-index-miss-total
  (counter-metric
    "blaze_rocksdb_block_cache_index_miss_total"
    "Returns the number of times cache miss occurred when accessing index block from block cache."
    TickerType/BLOCK_CACHE_INDEX_MISS))


(def ^:private block-cache-index-hit-total
  (counter-metric
    "blaze_rocksdb_block_cache_index_hit_total"
    "Returns the number of times cache hit occurred when accessing index block from block cache."
    TickerType/BLOCK_CACHE_INDEX_HIT))


(def ^:private block-cache-index-add-total
  (counter-metric
    "blaze_rocksdb_block_cache_index_add_total"
    "Returns the number of index blocks added to block cache."
    TickerType/BLOCK_CACHE_INDEX_ADD))


(def ^:private block-cache-index-insert-bytes-total
  (counter-metric
    "blaze_rocksdb_block_cache_index_insert_bytes_total"
    "Returns the number of bytes of index blocks inserted into block cache."
    TickerType/BLOCK_CACHE_INDEX_BYTES_INSERT))


(def ^:private block-cache-index-evict-bytes-total
  (counter-metric
    "blaze_rocksdb_block_cache_index_evict_bytes_total"
    "Returns the number of bytes of index blocks erased from block cache."
    TickerType/BLOCK_CACHE_INDEX_BYTES_EVICT))


(def ^:private block-cache-filter-miss-total
  (counter-metric
    "blaze_rocksdb_block_cache_filter_miss_total"
    "Returns the number of times cache miss when accessing filter block from block cache."
    TickerType/BLOCK_CACHE_FILTER_MISS))


(def ^:private block-cache-filter-hit-total
  (counter-metric
    "blaze_rocksdb_block_cache_filter_hit_total"
    "Returns the number of times cache hit when accessing filter block from block cache."
    TickerType/BLOCK_CACHE_FILTER_HIT))


(def ^:private block-cache-filter-add-total
  (counter-metric
    "blaze_rocksdb_block_cache_filter_add_total"
    "Returns the number of filter blocks added to block cache."
    TickerType/BLOCK_CACHE_FILTER_ADD))


(def ^:private block-cache-filter-insert-bytes-total
  (counter-metric
    "blaze_rocksdb_block_cache_filter_insert_bytes_total"
    "Returns the number of bytes of filter blocks inserted into block cache."
    TickerType/BLOCK_CACHE_FILTER_BYTES_INSERT))


(def ^:private block-cache-filter-evict-bytes-total
  (counter-metric
    "blaze_rocksdb_block_cache_filter_evict_bytes_total"
    "Returns the number of bytes of filter blocks erased from block cache."
    TickerType/BLOCK_CACHE_FILTER_BYTES_EVICT))


(def ^:private memtable-hit-total
  (counter-metric
    "blaze_rocksdb_memtable_hit_total"
    "Returns the number of memtable hits."
    TickerType/MEMTABLE_HIT))


(def ^:private memtable-miss-total
  (counter-metric
    "blaze_rocksdb_memtable_miss_total"
    "Returns the number of memtable misses."
    TickerType/MEMTABLE_MISS))


(def ^:private get-hit-l0-total
  (counter-metric
    "blaze_rocksdb_get_hit_l0_total"
    "Returns the number of Get() queries served by L0."
    TickerType/GET_HIT_L0))


(def ^:private get-hit-l1-total
  (counter-metric
    "blaze_rocksdb_get_hit_l1_total"
    "Returns the number of Get() queries served by L1."
    TickerType/GET_HIT_L1))


(def ^:private get-hit-l2-and-up-total
  (counter-metric
    "blaze_rocksdb_get_hit_l2_and_up_total"
    "Returns the number of Get() queries served by L2 and up."
    TickerType/GET_HIT_L2_AND_UP))


(def ^:private keys-read-total
  (counter-metric
    "blaze_rocksdb_keys_read_total"
    "Returns the number of keys read."
    TickerType/NUMBER_KEYS_READ))


(def ^:private keys-written-total
  (counter-metric
    "blaze_rocksdb_keys_written_total"
    "Returns the number of keys written."
    TickerType/NUMBER_KEYS_WRITTEN))


(def ^:private keys-updated-total
  (counter-metric
    "blaze_rocksdb_keys_updated_total"
    "Returns the number of keys updated."
    TickerType/NUMBER_KEYS_UPDATED))


(def ^:private seek-total
  (counter-metric
    "blaze_rocksdb_seek_total"
    "Returns the number of calls to seek."
    TickerType/NUMBER_DB_SEEK))


(def ^:private next-total
  (counter-metric
    "blaze_rocksdb_next_total"
    "Returns the number of calls to next."
    TickerType/NUMBER_DB_NEXT))


(def ^:private prev-total
  (counter-metric
    "blaze_rocksdb_prev_total"
    "Returns the number of calls to prev."
    TickerType/NUMBER_DB_PREV))


(def ^:private file-opens-total
  (counter-metric
    "blaze_rocksdb_file_opens_total"
    "Returns the number of file opens."
    TickerType/NO_FILE_OPENS))


(def ^:private file-closes-total
  (counter-metric
    "blaze_rocksdb_file_closes_total"
    "Returns the number of file closes."
    TickerType/NO_FILE_CLOSES))


(def ^:private file-errors-total
  (counter-metric
    "blaze_rocksdb_file_errors_total"
    "Returns the number of file errors."
    TickerType/NO_FILE_ERRORS))


(def ^:private bloom-filter-useful-total
  (counter-metric
    "blaze_rocksdb_bloom_filter_useful_total"
    "Number of times bloom filter has avoided file reads."
    TickerType/BLOOM_FILTER_USEFUL))


(def ^:private bloom-filter-full-positive-total
  (counter-metric
    "blaze_rocksdb_bloom_filter_full_positive_total"
    "Number of times bloom FullFilter has not avoided the reads."
    TickerType/BLOOM_FILTER_FULL_POSITIVE))


(def ^:private bloom-filter-full-true-positive-total
  (counter-metric
    "blaze_rocksdb_bloom_filter_full_true_positive_total"
    "Number of times bloom FullFilter has not avoided the reads and data actually exist."
    TickerType/BLOOM_FILTER_FULL_TRUE_POSITIVE))


(def ^:private blocks-compressed-total
  (counter-metric
    "blaze_rocksdb_blocks_compressed_total"
    "Number of blocks compressed."
    TickerType/NUMBER_BLOCK_COMPRESSED))


(def ^:private blocks-decompressed-total
  (counter-metric
    "blaze_rocksdb_blocks_decompressed_total"
    "Number of blocks decompressed."
    TickerType/NUMBER_BLOCK_DECOMPRESSED))


(def ^:private blocks-not-compressed-total
  (counter-metric
    "blaze_rocksdb_blocks_not_compressed_total"
    "Number of blocks not compressed."
    TickerType/NUMBER_BLOCK_NOT_COMPRESSED))


(defn stats-collector [stats]
  (metrics/collector
    [(block-cache-data-miss-total stats)
     (block-cache-data-hit-total stats)
     (block-cache-data-add-total stats)
     (block-cache-data-insert-bytes-total stats)
     (block-cache-index-miss-total stats)
     (block-cache-index-hit-total stats)
     (block-cache-index-add-total stats)
     (block-cache-index-insert-bytes-total stats)
     (block-cache-index-evict-bytes-total stats)
     (block-cache-filter-miss-total stats)
     (block-cache-filter-hit-total stats)
     (block-cache-filter-add-total stats)
     (block-cache-filter-insert-bytes-total stats)
     (block-cache-filter-evict-bytes-total stats)
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
     (blocks-not-compressed-total stats)]))
