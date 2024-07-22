(ns blaze.db.kv.rocksdb.metrics
  (:require
   [blaze.db.kv.rocksdb.protocols :as p]
   [blaze.metrics.core :as metrics])
  (:import
   [org.rocksdb Cache HistogramType Statistics TickerType]))

(set! *warn-on-reflection* true)

(defn- sample-xf [ticker-type value-f]
  (map
   (fn [[name stats]]
     {:label-values [name]
      :value (value-f (.getTickerCount ^Statistics stats ticker-type))})))

(defn- histogram-xf [histogram-type value-f]
  (map
   (fn [[name stats]]
     {:label-values [name]
      :value (value-f (.getSum (.getHistogramData ^Statistics stats histogram-type)))})))

(defn- samples [ticker-type value-f stats]
  (into [] (sample-xf ticker-type value-f) stats))

(defn- histogram-samples [histogram-type value-f stats]
  (into [] (histogram-xf histogram-type value-f) stats))

(defn- counter-metric
  ([name help ticker-type]
   (counter-metric name help ticker-type identity))
  ([name help ticker-type value-f]
   (fn [stats]
     (metrics/counter-metric name help ["name"] (samples ticker-type value-f stats)))))

(defn- histogram-metric
  ([name help histogram-type value-f]
   (fn [stats]
     (metrics/counter-metric name help ["name"] (histogram-samples histogram-type value-f stats)))))

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

(def ^:private file-errors-total
  (counter-metric
   "blaze_rocksdb_file_errors_total"
   "Returns the number of file errors."
   TickerType/NO_FILE_ERRORS))

(def ^:private stall-seconds-total
  (counter-metric
   "blaze_rocksdb_stall_seconds_total"
   "Returns the total number of seconds the writer had to wait for compaction or flush to finish."
   TickerType/STALL_MICROS
   #(/ (double %) 1e6)))

(def ^:private bloom-filter-useful-total
  (counter-metric
   "blaze_rocksdb_bloom_filter_useful_total"
   "Number of times Bloom filter has avoided file reads."
   TickerType/BLOOM_FILTER_USEFUL))

(def ^:private bloom-filter-full-positive-total
  (counter-metric
   "blaze_rocksdb_bloom_filter_full_positive_total"
   "Number of times Bloom FullFilter has not avoided the reads."
   TickerType/BLOOM_FILTER_FULL_POSITIVE))

(def ^:private bloom-filter-full-true-positive-total
  (counter-metric
   "blaze_rocksdb_bloom_filter_full_true_positive_total"
   "Number of times Bloom FullFilter has not avoided the reads and data actually exist."
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

(def ^:private blocks-compression-bypassed-total
  (counter-metric
   "blaze_rocksdb_blocks_compression_bypassed_total"
   "Number of blocks were compression was bypassed."
   TickerType/NUMBER_BLOCK_COMPRESSION_BYPASSED))

(def ^:private blocks-compression-rejected-total
  (counter-metric
   "blaze_rocksdb_blocks_compression_rejected_total"
   "Number of blocks were compression was rejected."
   TickerType/NUMBER_BLOCK_COMPRESSION_REJECTED))

(def ^:private compression-input-bytes-total
  (counter-metric
   "blaze_rocksdb_compression_input_bytes_total"
   "Number of input bytes (uncompressed) to compression for SST blocks that are stored compressed."
   TickerType/BYTES_COMPRESSED_FROM))

(def ^:private compression-output-bytes-total
  (counter-metric
   "blaze_rocksdb_compression_output_bytes_total"
   "Number of output bytes (compressed) to compression for SST blocks that are stored compressed."
   TickerType/BYTES_COMPRESSED_TO))

(def ^:private compression-bypassed-bytes-total
  (counter-metric
   "blaze_rocksdb_compression_bypassed_bytes_total"
   "Number of uncompressed bytes for SST blocks that are stored uncompressed because compression type is kNoCompression, or some error case caused compression not to run or produce an output."
   TickerType/BYTES_COMPRESSION_BYPASSED))

(def ^:private compression-rejected-bytes-total
  (counter-metric
   "blaze_rocksdb_compression_rejected_bytes_total"
   "Number of input bytes (uncompressed) to compression for SST blocks that are stored uncompressed because the compression result was rejected, either because the ratio was not acceptable (see CompressionOptions::max_compressed_bytes_per_kb) or found invalid by the `verify_compression` option."
   TickerType/BYTES_COMPRESSION_REJECTED))

(def ^:private decompression-input-bytes-total
  (counter-metric
   "blaze_rocksdb_decompression_input_bytes_total"
   "Number of input bytes (compressed) to decompression in reading compressed SST blocks from storage."
   TickerType/BYTES_DECOMPRESSED_FROM))

(def ^:private decompression-output-bytes-total
  (counter-metric
   "blaze_rocksdb_decompression_output_bytes_total"
   "Number of output bytes (uncompressed) from decompression in reading compressed SST blocks from storage."
   TickerType/BYTES_DECOMPRESSED_TO))

(def ^:private iterators-created-total
  (counter-metric
   "blaze_rocksdb_iterators_created_total"
   "Returns the total number of iterators created."
   TickerType/NO_ITERATOR_CREATED))

(def ^:private iterators-deleted-total
  (counter-metric
   "blaze_rocksdb_iterators_deleted_total"
   "Returns the total number of iterators deleted."
   TickerType/NO_ITERATOR_DELETED))

(def ^:private wal-syncs-total
  (counter-metric
   "blaze_rocksdb_wal_syncs_total"
   "Returns the total number of WAL syncs."
   TickerType/WAL_FILE_SYNCED))

(def ^:private wal-bytes-total
  (counter-metric
   "blaze_rocksdb_wal_bytes_total"
   "Returns the total number of bytes written to WAL."
   TickerType/WAL_FILE_BYTES))

(def ^:private flush-seconds-total
  (histogram-metric
   "blaze_rocksdb_flush_seconds_total"
   "Returns the total number of seconds spent flushing memtables to disk."
   HistogramType/FLUSH_TIME
   #(/ (double %) 1e6)))

(def ^:private compaction-seconds-total
  (histogram-metric
   "blaze_rocksdb_compaction_seconds_total"
   "Returns the total number of seconds spent in compaction."
   HistogramType/COMPACTION_TIME
   #(/ (double %) 1e6)))

(def ^:private compression-seconds-total
  (histogram-metric
   "blaze_rocksdb_compression_seconds_total"
   "Returns the total number of seconds spent in compression."
   HistogramType/COMPRESSION_TIMES_NANOS
   #(/ (double %) 1e9)))

(def ^:private decompression-seconds-total
  (histogram-metric
   "blaze_rocksdb_decompression_seconds_total"
   "Returns the total number of seconds spent in decompression."
   HistogramType/DECOMPRESSION_TIMES_NANOS
   #(/ (double %) 1e9)))

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
     (block-cache-filter-miss-total stats)
     (block-cache-filter-hit-total stats)
     (block-cache-filter-add-total stats)
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
     (file-errors-total stats)
     (stall-seconds-total stats)
     (bloom-filter-useful-total stats)
     (bloom-filter-full-positive-total stats)
     (bloom-filter-full-true-positive-total stats)
     (blocks-compressed-total stats)
     (blocks-decompressed-total stats)
     (blocks-compression-bypassed-total stats)
     (blocks-compression-rejected-total stats)
     (compression-input-bytes-total stats)
     (compression-output-bytes-total stats)
     (compression-bypassed-bytes-total stats)
     (compression-rejected-bytes-total stats)
     (decompression-input-bytes-total stats)
     (decompression-output-bytes-total stats)
     (iterators-created-total stats)
     (iterators-deleted-total stats)
     (wal-syncs-total stats)
     (wal-bytes-total stats)
     (flush-seconds-total stats)
     (compaction-seconds-total stats)
     (compression-seconds-total stats)
     (decompression-seconds-total stats)]))

(defn block-cache-collector [block-cache]
  (metrics/collector
    [(metrics/gauge-metric
      "blaze_rocksdb_block_cache_usage_bytes"
      "Returns the memory size for the entries in the RocksDB block cache."
      []
      [{:label-values []
        :value (.getUsage ^Cache block-cache)}])
     (metrics/gauge-metric
      "blaze_rocksdb_block_cache_pinned_usage_bytes"
      "Returns the memory size for the entries pinned in the RocksDB block cache."
      []
      [{:label-values []
        :value (.getPinnedUsage ^Cache block-cache)}])]))

(defn table-reader-collector [stores]
  (metrics/collector
    [(metrics/gauge-metric
      "blaze_rocksdb_table_reader_usage_bytes"
      "Returns the memory usage of the table reader."
      ["name" "column_family"]
      (for [[name store] stores
            column-family (p/-column-families store)]
        {:label-values [name (clojure.core/name column-family)]
         :value (p/-long-property store column-family "rocksdb.estimate-table-readers-mem")}))]))
