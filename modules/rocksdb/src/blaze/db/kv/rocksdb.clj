(ns blaze.db.kv.rocksdb
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.db.kv :as kv]
    [clojure.java.io :as io]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.lang AutoCloseable]
    [java.io Closeable]
    [org.rocksdb RocksDB RocksIterator WriteOptions WriteBatch
                 Options ColumnFamilyHandle DBOptions
                 ColumnFamilyDescriptor CompressionType ColumnFamilyOptions
                 BlockBasedTableConfig Statistics LRUCache BloomFilter
                 CompactRangeOptions]
    [java.util ArrayList]))


(set! *warn-on-reflection* true)


(defn- iterator->key [^RocksIterator i]
  (when (.isValid i)
    (.key i)))


(deftype RocksKvIterator [^RocksIterator i]
  kv/KvIterator
  (-seek [_ target]
    (.seek i ^bytes target)
    (iterator->key i))

  (-seek-for-prev [_ target]
    (.seekForPrev i ^bytes target)
    (iterator->key i))

  (seek-to-first [_]
    (.seekToFirst i)
    (iterator->key i))

  (seek-to-last [_]
    (.seekToLast i)
    (iterator->key i))

  (next [_]
    (.next i)
    (iterator->key i))

  (prev [_]
    (.prev i)
    (iterator->key i))

  (value [_]
    (.value i))

  Closeable
  (close [_]
    (.close i)))


(defn- get-cfh ^ColumnFamilyHandle [cfhs column-family]
  (or (get cfhs column-family)
      (throw-anom
        ::anom/not-found
        (format "column family `%s` not found" (name column-family)))))


(deftype RocksKvSnapshot [^RocksDB db cfhs]
  kv/KvSnapshot
  (new-iterator [_]
    (->RocksKvIterator (.newIterator db)))

  (new-iterator [_ column-family]
    (->RocksKvIterator (.newIterator db (get-cfh cfhs column-family))))

  (snapshot-get [_ k]
    (.get db ^bytes k))

  (snapshot-get [_ column-family k]
    (.get db (get-cfh cfhs column-family) ^bytes k))

  Closeable
  (close [_]))


(deftype RocksKvStore [^RocksDB db ^Options opts ^WriteOptions write-opts cfhs]
  kv/KvStore
  (new-snapshot [_]
    (->RocksKvSnapshot db cfhs))

  (get [_ k]
    (.get db k))

  (get [_ column-family k]
    (.get db ^ColumnFamilyHandle (get-cfh cfhs column-family) ^bytes k))

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
  ([[kw]]
   (ColumnFamilyDescriptor.
     (.getBytes (name kw))))
  ([block-cache
    [kw {:keys [write-buffer-size-in-mb
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
     (.getBytes (name kw))
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
   {:keys [sync?
           disable-wal?
           max-background-jobs
           compaction-readahead-size]
    :or {max-background-jobs 2
         compaction-readahead-size 0}}
   column-families]
  (let [opts (doto (DBOptions.)
               (.setCreateIfMissing true)
               (.setStatsDumpPeriodSec 60)
               (.setStatistics (Statistics.))
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


(defmethod ig/init-key :blaze.db.kv.rocksdb/block-cache
  [_ {:keys [size-in-mb] :or {size-in-mb 128}}]
  (log/info (format "Init RocksDB block cache of %d MB" size-in-mb))
  (RocksDB/loadLibrary)
  (LRUCache. (bit-shift-left size-in-mb 20)))


(defmethod ig/halt-key! :blaze.db.kv.rocksdb/block-cache
  [_ cache]
  (log/info "Shutdown RocksDB block cache")
  (.close ^AutoCloseable cache))


(defn- init-log-msg [dir opts]
  (format "Open RocksDB key-value store in `%s` with options: %s"
          dir (pr-str opts)))


(defmethod ig/init-key :blaze.db.kv/rocksdb
  [_ {:keys [dir block-cache opts column-families]}]
  (log/info (init-log-msg dir opts))
  (when-not (.isDirectory (io/file dir))
    (create-rocksdb-kv-store dir (dissoc column-families :default)))
  (init-rocksdb-kv-store dir block-cache opts (merge {:default nil} column-families)))


(defmethod ig/halt-key! :blaze.db.kv/rocksdb
  [_ store]
  (log/info "Close RocksDB key-value store")
  (.close ^Closeable store))


(comment
  (import [org.rocksdb ColumnFamilyDescriptor ColumnFamilyOptions])

  (create-rocksdb-kv-store
    "./data"
    [(ColumnFamilyDescriptor.
       (.getBytes "i")
       (doto (ColumnFamilyOptions.)
         (.setMergeOperatorName "uint64add")))])

  (def kv
    (init-rocksdb-kv-store
      "./data"
      {}
      [(ColumnFamilyDescriptor.
         RocksDB/DEFAULT_COLUMN_FAMILY)
       (ColumnFamilyDescriptor.
         (.getBytes "i")
         (doto (ColumnFamilyOptions.)
           (.setMergeOperatorName "uint64add")))]))

  (.close kv)

  (kv/write kv [[:merge :i (byte-array [1]) (byte-array [0 0 0 0 0 0 0 1])]])

  (vec (kv/get kv :i (byte-array [1])))
  )
