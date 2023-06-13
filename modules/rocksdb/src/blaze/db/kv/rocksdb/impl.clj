(ns blaze.db.kv.rocksdb.impl
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.coll.core :as coll])
  (:import
    [clojure.lang ILookup]
    [org.rocksdb
     BlockBasedTableConfig BloomFilter BuiltinComparator ColumnFamilyDescriptor
     ColumnFamilyHandle ColumnFamilyOptions CompressionType DBOptions
     RocksDBException Statistics Status$Code WriteBatchInterface WriteOptions]))


(set! *warn-on-reflection* true)


(defn column-family-descriptor
  {:arglists '([block-cache [key opts]])}
  [block-cache
   [key
    {:keys [write-buffer-size-in-mb
            max-write-buffer-number
            level0-file-num-compaction-trigger
            min-write-buffer-number-to-merge
            max-bytes-for-level-base-in-mb
            target-file-size-base-in-mb
            block-size
            bloom-filter?
            memtable-whole-key-filtering?
            optimize-filters-for-hits?
            reverse-comparator?
            merge-operator]
     :or {write-buffer-size-in-mb 64
          max-write-buffer-number 2
          level0-file-num-compaction-trigger 4
          min-write-buffer-number-to-merge 1
          max-bytes-for-level-base-in-mb 256
          target-file-size-base-in-mb 64
          block-size (bit-shift-left 4 10)
          bloom-filter? false
          memtable-whole-key-filtering? false
          optimize-filters-for-hits? false
          reverse-comparator? false}}]]
  (ColumnFamilyDescriptor.
    (.getBytes (name key))
    (cond->
      (doto (ColumnFamilyOptions.)
        (.setLevelCompactionDynamicLevelBytes true)
        (.setCompressionType CompressionType/LZ4_COMPRESSION)
        (.setBottommostCompressionType CompressionType/ZSTD_COMPRESSION)
        (.setWriteBufferSize (bit-shift-left write-buffer-size-in-mb 20))
        (.setMaxWriteBufferNumber (long max-write-buffer-number))
        (.setMaxBytesForLevelBase (bit-shift-left max-bytes-for-level-base-in-mb 20))
        (.setLevel0FileNumCompactionTrigger (long level0-file-num-compaction-trigger))
        (.setMinWriteBufferNumberToMerge (long min-write-buffer-number-to-merge))
        (.setTargetFileSizeBase (bit-shift-left target-file-size-base-in-mb 20))
        (.setTableFormatConfig
          (cond->
            (doto (BlockBasedTableConfig.)
              (.setVerifyCompression false)
              (.setBlockSize block-size))
            (nil? block-cache)
            (.setNoBlockCache true)
            (some? block-cache)
            (-> (.setBlockCache block-cache)
                (.setCacheIndexAndFilterBlocks true)
                (.setPinL0FilterAndIndexBlocksInCache true))
            bloom-filter?
            (.setFilterPolicy (BloomFilter. 10 false))
            bloom-filter?
            (.setWholeKeyFiltering true))))
      memtable-whole-key-filtering?
      (.setMemtableWholeKeyFiltering true)
      optimize-filters-for-hits?
      (.setOptimizeFiltersForHits true)
      reverse-comparator?
      (.setComparator BuiltinComparator/REVERSE_BYTEWISE_COMPARATOR)
      merge-operator
      (.setMergeOperatorName (name merge-operator)))))


(defn db-options
  ^DBOptions
  [stats
   {:keys [wal-dir
           max-background-jobs
           compaction-readahead-size]
    :or {wal-dir ""
         max-background-jobs 2
         compaction-readahead-size 0}}]
  (doto (DBOptions.)
    (.setStatistics ^Statistics stats)
    (.setWalDir (str wal-dir))
    (.setMaxBackgroundJobs (long max-background-jobs))
    (.setCompactionReadaheadSize (long compaction-readahead-size))
    (.setEnablePipelinedWrite true)
    (.setCreateIfMissing true)
    (.setCreateMissingColumnFamilies true)))


(defn write-options [{:keys [sync? disable-wal?]}]
  (cond-> (WriteOptions.)
    sync?
    (.setSync true)
    disable-wal?
    (.setDisableWAL true)))


(defn- column-family-not-found-msg [column-family]
  (format "column family `%s` not found" (name column-family)))


(defn get-cfh ^ColumnFamilyHandle [cfhs column-family]
  (let [handle (.valAt ^ILookup cfhs column-family)]
    (if (nil? handle)
      (throw-anom (ba/not-found (column-family-not-found-msg column-family)))
      handle)))


(defn put-wb! [cfhs ^WriteBatchInterface wb entries]
  (run!
    (fn [entry]
      (if (= 3 (coll/count entry))
        (let [column-family (coll/nth entry 0)
              key (coll/nth entry 1)
              value (coll/nth entry 2)]
          (.put wb (get-cfh cfhs column-family) ^bytes key ^bytes value))
        (let [key (coll/nth entry 0)
              value (coll/nth entry 1)]
          (.put wb ^bytes key ^bytes value))))
    entries))


(defn delete-wb! [^WriteBatchInterface wb ks]
  (run! #(.delete wb ^bytes %) ks))


(defn write-wb! [cfhs ^WriteBatchInterface wb entries]
  (run!
    (fn [[op column-family k v]]
      (if (keyword? column-family)
        (case op
          :put (.put wb (get-cfh cfhs column-family) ^bytes k ^bytes v)
          :merge (.merge wb (get-cfh cfhs column-family) ^bytes k ^bytes v)
          :delete (.delete wb (get-cfh cfhs column-family) ^bytes k))
        (case op
          :put (.put wb ^bytes column-family ^bytes k)
          :merge (.merge wb ^bytes column-family ^bytes k)
          :delete (.delete wb ^bytes column-family))))
    entries))


(defn property-error [^RocksDBException e name]
  (condp identical? (some-> (.getStatus e) (.getCode))
    Status$Code/NotFound
    (ba/not-found (format "Property with name `%s` was not found." name))
    (ba/fault (ex-message e))))


(defn column-family-property-error [^RocksDBException e column-family name]
  (condp identical? (some-> (.getStatus e) (.getCode))
    Status$Code/NotFound
    (ba/not-found (format "Property with name `%s` was not found on column-family with name `%s`." name (clojure.core/name column-family)))
    (ba/fault (ex-message e))))
