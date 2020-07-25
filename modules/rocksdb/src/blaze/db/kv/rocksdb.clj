(ns blaze.db.kv.rocksdb
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.db.kv :as kv]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.lang AutoCloseable]
    [java.io Closeable]
    [java.util ArrayList]
    [io.prometheus.client Collector CounterMetricFamily]
    [org.rocksdb
     RocksDB RocksIterator WriteOptions WriteBatch Options ColumnFamilyHandle
     DBOptions ColumnFamilyDescriptor CompressionType ColumnFamilyOptions
     BlockBasedTableConfig Statistics LRUCache BloomFilter CompactRangeOptions
     TickerType Snapshot ReadOptions]))


(set! *warn-on-reflection* true)


(deftype RocksKvIterator [^RocksIterator i]
  kv/KvIterator
  (-valid [_]
    (.isValid i))

  (-seek-to-first [_]
    (.seekToFirst i))

  (-seek-to-last [_]
    (.seekToLast i))

  (-seek [_ target]
    (.seek i ^bytes target))

  (-seek-for-prev [_ target]
    (.seekForPrev i ^bytes target))

  (-next [_]
    (.next i))

  (-prev [_]
    (.prev i))

  (-key [_]
    (.key i))

  (-key [_ buf]
    (.key i buf))

  (-value [_]
    (.value i))

  (-value [_ buf]
    (.value i buf))

  Closeable
  (close [_]
    (.close i)))


(defn- get-cfh ^ColumnFamilyHandle [cfhs column-family]
  (or (get cfhs column-family)
      (throw-anom
        ::anom/not-found
        (format "column family `%s` not found" (name column-family)))))


(deftype RocksKvSnapshot
  [^RocksDB db ^Snapshot snapshot ^ReadOptions read-opts cfhs]
  kv/KvSnapshot
  (new-iterator [_]
    (->RocksKvIterator (.newIterator db read-opts)))

  (new-iterator [_ column-family]
    (->RocksKvIterator (.newIterator db (get-cfh cfhs column-family) read-opts)))

  (snapshot-get [_ k]
    (.get db read-opts ^bytes k))

  (snapshot-get [_ column-family k]
    (.get db (get-cfh cfhs column-family) read-opts ^bytes k))

  Closeable
  (close [_]
    (.close read-opts)
    (.releaseSnapshot db snapshot)))


(deftype RocksKvStore [^RocksDB db ^Options opts ^WriteOptions write-opts cfhs]
  kv/KvStore
  (new-snapshot [_]
    (let [snapshot (.getSnapshot db)]
      (->RocksKvSnapshot db snapshot (.setSnapshot (ReadOptions.) snapshot) cfhs)))

  (-get [_ k]
    (.get db k))

  (-get [_ column-family k]
    (.get db ^ColumnFamilyHandle (get-cfh cfhs column-family) ^bytes k))

  (-multi-get [_ keys]
    (loop [[k & ks] keys
           [v & vs] (.multiGetAsList db keys)
           res {}]
      (if k
        (if v
          (recur ks vs (assoc res k v))
          (recur ks vs res))
        res)))

  (-put [_ entries]
    (with-open [wb (WriteBatch.)]
      (doseq [[column-family k v] entries]
        (if (keyword? column-family)
          (.put wb (get-cfh cfhs column-family) ^bytes k ^bytes v)
          (.put wb ^bytes column-family ^bytes k)))
      (.write db write-opts wb)))

  (-put [_ key value]
    (.put db key value))

  (delete [_ ks]
    (with-open [wb (WriteBatch.)]
      (doseq [k ks]
        (if (vector? k)
          (let [[column-family k] k]
            (.delete wb (get-cfh cfhs column-family) k))
          (.delete wb k)))
      (.write db write-opts wb)))

  (write [_ entries]
    (with-open [wb (WriteBatch.)]
      (doseq [[op column-family k v] entries]
        (if (keyword? column-family)
          (case op
            :put (.put wb (get-cfh cfhs column-family) ^bytes k ^bytes v)
            :merge (.merge wb (get-cfh cfhs column-family) ^bytes k ^bytes v)
            :delete (.delete wb (get-cfh cfhs column-family) ^bytes k))
          (case op
            :put (.put wb ^bytes column-family ^bytes k)
            :merge (.merge wb ^bytes column-family ^bytes k)
            :delete (.delete wb ^bytes column-family))))
      (.write db write-opts wb)))

  Closeable
  (close [_]
    (.close db)
    (.close opts)
    (.close write-opts)))


(defn compact-range
  ([store]
   (.compactRange ^RocksDB (.db ^RocksKvStore store)))
  ([^RocksKvStore store column-family change-level target-level]
   (assert (get (.cfhs store) column-family))
   (.compactRange
     ^RocksDB (.db store)
     ^ColumnFamilyHandle (get (.cfhs store) column-family)
     nil
     nil
     (doto (CompactRangeOptions.)
       (.setChangeLevel change-level)
       (.setTargetLevel target-level)))))


(defn- index-column-family-handles [column-family-handles]
  (into
    {}
    (map #(vector (keyword (String. (.getName ^ColumnFamilyHandle %))) %))
    column-family-handles))


(defn- column-family-descriptor
  {:arglists '([[key]] [block-cache [key opts]])}
  ([[key]]
   (ColumnFamilyDescriptor.
     (.getBytes (name key))))
  ([block-cache
    [key {:keys [write-buffer-size-in-mb
                 max-write-buffer-number
                 level0-file-num-compaction-trigger
                 min-write-buffer-number-to-merge
                 max-bytes-for-level-base-in-mb
                 block-size
                 bloom-filter?]
          :or {write-buffer-size-in-mb 4
               max-write-buffer-number 2
               level0-file-num-compaction-trigger 4
               min-write-buffer-number-to-merge 1
               max-bytes-for-level-base-in-mb 10
               block-size (bit-shift-left 4 10)
               bloom-filter? false}}]]
   (ColumnFamilyDescriptor.
     (.getBytes (name key))
     (doto (ColumnFamilyOptions.)
       (.setLevelCompactionDynamicLevelBytes true)
       (.setCompressionType CompressionType/LZ4_COMPRESSION)
       (.setBottommostCompressionType CompressionType/ZSTD_COMPRESSION)
       (.setWriteBufferSize (bit-shift-left ^long write-buffer-size-in-mb 20))
       (.setMaxWriteBufferNumber ^long max-write-buffer-number)
       (.setMaxBytesForLevelBase (bit-shift-left ^long max-bytes-for-level-base-in-mb 20))
       (.setLevel0FileNumCompactionTrigger ^long level0-file-num-compaction-trigger)
       (.setMinWriteBufferNumberToMerge ^long min-write-buffer-number-to-merge)
       (.setTableFormatConfig
         (cond->
           (doto (BlockBasedTableConfig.)
             (.setVerifyCompression false)
             (.setCacheIndexAndFilterBlocks true)
             (.setPinL0FilterAndIndexBlocksInCache true)
             (.setBlockSize block-size)
             (.setBlockCache block-cache))
           bloom-filter?
           (.setFilterPolicy (BloomFilter. 10 false))))))))


(defn init-rocksdb-kv-store
  [dir
   block-cache
   stats
   {:keys [sync?
           disable-wal?
           max-background-jobs
           compaction-readahead-size]
    :or {max-background-jobs 2
         compaction-readahead-size 0}}
   column-families]
  (let [opts (doto (DBOptions.)
               (.setStatsDumpPeriodSec 60)
               (.setStatistics ^Statistics stats)
               (.setMaxBackgroundJobs ^long max-background-jobs)
               (.setCompactionReadaheadSize ^long compaction-readahead-size)
               (.setBytesPerSync 1048576))
        cfds (map (partial column-family-descriptor block-cache) column-families)
        cfhs (ArrayList.)
        db (try
             (RocksDB/open opts dir cfds cfhs)
             (finally (.close opts)))
        write-opts (WriteOptions.)]

    (when sync?
      (.setSync write-opts true))
    (when disable-wal?
      (.setDisableWAL write-opts true))
    (->RocksKvStore db opts write-opts (index-column-family-handles cfhs))))


(defn create-rocksdb-kv-store [dir column-families]
  (let [opts (doto (Options.)
               (.setCreateIfMissing true))
        ^RocksDB db (try
                      (RocksDB/open opts dir)
                      (finally (.close opts)))]
    (try
      (.createColumnFamilies db (map column-family-descriptor column-families))
      (finally
        (.close db)))))


(defn- stats-collector [^Statistics stats]
  (proxy [Collector] []
    (collect []
      [(CounterMetricFamily.
         "blaze_rocksdb_block_cache_miss_total"
         "blaze_rocksdb_block_cache_miss_total"
         (double (.getTickerCount stats TickerType/BLOCK_CACHE_MISS)))
       (CounterMetricFamily.
         "blaze_rocksdb_block_cache_hit_total"
         "blaze_rocksdb_block_cache_hit_total"
         (double (.getTickerCount stats TickerType/BLOCK_CACHE_HIT)))
       (CounterMetricFamily.
         "blaze_rocksdb_keys_read_total"
         "blaze_rocksdb_keys_read_total"
         (double (.getTickerCount stats TickerType/NUMBER_KEYS_READ)))
       (CounterMetricFamily.
         "blaze_rocksdb_keys_written_total"
         "blaze_rocksdb_keys_written_total"
         (double (.getTickerCount stats TickerType/NUMBER_KEYS_WRITTEN)))
       (CounterMetricFamily.
         "blaze_rocksdb_keys_updated_total"
         "blaze_rocksdb_keys_updated_total"
         (double (.getTickerCount stats TickerType/NUMBER_KEYS_UPDATED)))
       (CounterMetricFamily.
         "blaze_rocksdb_seek_total"
         "blaze_rocksdb_seek_total"
         (double (.getTickerCount stats TickerType/NUMBER_DB_SEEK)))
       (CounterMetricFamily.
         "blaze_rocksdb_next_total"
         "blaze_rocksdb_next_total"
         (double (.getTickerCount stats TickerType/NUMBER_DB_NEXT)))
       (CounterMetricFamily.
         "blaze_rocksdb_prev_total"
         "blaze_rocksdb_prev_total"
         (double (.getTickerCount stats TickerType/NUMBER_DB_PREV)))
       (CounterMetricFamily.
         "blaze_rocksdb_file_open_total"
         "blaze_rocksdb_file_open_total"
         (double (.getTickerCount stats TickerType/NO_FILE_OPENS)))
       (CounterMetricFamily.
         "blaze_rocksdb_file_close_total"
         "blaze_rocksdb_file_close_total"
         (double (.getTickerCount stats TickerType/NO_FILE_CLOSES)))
       (CounterMetricFamily.
         "blaze_rocksdb_file_error_total"
         "blaze_rocksdb_file_error_total"
         (double (.getTickerCount stats TickerType/NO_FILE_ERRORS)))
       (CounterMetricFamily.
         "blaze_rocksdb_bloom_filter_useful_total"
         "Number of times bloom filter has avoided file reads."
         (double (.getTickerCount stats TickerType/BLOOM_FILTER_USEFUL)))
       (CounterMetricFamily.
         "blaze_rocksdb_bloom_filter_full_positive_total"
         "Number of times bloom FullFilter has not avoided the reads."
         (double (.getTickerCount stats TickerType/BLOOM_FILTER_FULL_POSITIVE)))
       (CounterMetricFamily.
         "blaze_rocksdb_bloom_filter_full_true_positive_total"
         "Number of times bloom FullFilter has not avoided the reads and data actually exist."
         (double (.getTickerCount stats TickerType/BLOOM_FILTER_FULL_TRUE_POSITIVE)))])))


(defmethod ig/init-key ::block-cache
  [_ {:keys [size-in-mb] :or {size-in-mb 128}}]
  (log/info (format "Init RocksDB block cache of %d MB" size-in-mb))
  (RocksDB/loadLibrary)
  (LRUCache. (bit-shift-left size-in-mb 20)))


(defmethod ig/halt-key! ::block-cache
  [_ cache]
  (log/info "Shutdown RocksDB block cache")
  (.close ^AutoCloseable cache))


(s/def ::dir
  string?)


(defmethod ig/pre-init-spec :blaze.db.kv/rocksdb [_]
  (s/keys :req-un [::dir]))


(defn- init-log-msg [dir opts]
  (format "Open RocksDB key-value store in directory `%s` with options: %s. This can take up to several minutes due to forced compaction."
          dir (pr-str opts)))


(defmethod ig/init-key :blaze.db.kv/rocksdb
  [_ {:keys [dir block-cache stats opts column-families]}]
  (log/info (init-log-msg dir opts))
  (when-not (.isDirectory (io/file dir))
    (create-rocksdb-kv-store dir (dissoc column-families :default)))
  (init-rocksdb-kv-store dir block-cache stats opts (merge {:default nil} column-families)))


(defmethod ig/halt-key! :blaze.db.kv/rocksdb
  [_ store]
  (log/info "Close RocksDB key-value store")
  (.close ^Closeable store))


(defmethod ig/init-key ::stats
  [_ _]
  (log/info "Init RocksDB statistics")
  (Statistics.))


(defmethod ig/halt-key! ::stats
  [_ stats]
  (log/info "Shutdown RocksDB statistics")
  (.close ^AutoCloseable stats))


(defmethod ig/init-key ::stats-collector
  [_ {:keys [stats]}]
  (stats-collector stats))

(derive ::stats-collector :blaze.metrics/collector)
