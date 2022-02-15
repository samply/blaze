(ns blaze.db.kv.rocksdb.impl
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]])
  (:import
    [clojure.lang Indexed]
    [org.rocksdb
     ColumnFamilyHandle DBOptions ColumnFamilyDescriptor CompressionType
     ColumnFamilyOptions BlockBasedTableConfig Statistics BloomFilter
     BuiltinComparator WriteBatchInterface WriteOptions]))


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
          optimize-filters-for-hits? false
          reverse-comparator? false}}]]
  (ColumnFamilyDescriptor.
    (.getBytes (name key))
    (cond->
      (doto (ColumnFamilyOptions.)
        (.setLevelCompactionDynamicLevelBytes true)
        (.setCompressionType CompressionType/LZ4_COMPRESSION)
        (.setBottommostCompressionType CompressionType/ZSTD_COMPRESSION)
        (.setWriteBufferSize (bit-shift-left ^long write-buffer-size-in-mb 20))
        (.setMaxWriteBufferNumber ^long max-write-buffer-number)
        (.setMaxBytesForLevelBase (bit-shift-left ^long max-bytes-for-level-base-in-mb 20))
        (.setLevel0FileNumCompactionTrigger ^long level0-file-num-compaction-trigger)
        (.setMinWriteBufferNumberToMerge ^long min-write-buffer-number-to-merge)
        (.setTargetFileSizeBase (bit-shift-left ^long target-file-size-base-in-mb 20))
        (.setTableFormatConfig
          (cond->
            (doto (BlockBasedTableConfig.)
              (.setVerifyCompression false)
              (.setCacheIndexAndFilterBlocks true)
              (.setPinL0FilterAndIndexBlocksInCache true)
              (.setBlockSize block-size)
              (.setBlockCache block-cache))
            bloom-filter?
            (.setFilterPolicy (BloomFilter. 10 false)))))
      optimize-filters-for-hits?
      (.setOptimizeFiltersForHits true)
      reverse-comparator?
      (.setComparator BuiltinComparator/REVERSE_BYTEWISE_COMPARATOR)
      merge-operator
      (.setMergeOperatorName (name merge-operator)))))


(defn db-options
  [stats
   {:keys [max-background-jobs
           compaction-readahead-size]
    :or {max-background-jobs 2
         compaction-readahead-size 0}}]
  (doto (DBOptions.)
    (.setStatistics ^Statistics stats)
    (.setMaxBackgroundJobs ^long max-background-jobs)
    (.setCompactionReadaheadSize ^long compaction-readahead-size)
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
  (or (cfhs column-family)
      (throw-anom (ba/not-found (column-family-not-found-msg column-family)))))


(defn put-wb! [cfhs ^WriteBatchInterface wb entries]
  (run!
    (fn [^Indexed entry]
      (let [column-family (.nth entry 0)]
        (if (keyword? column-family)
          (.put wb (get-cfh cfhs column-family) ^bytes (.nth entry 1)
                ^bytes (.nth entry 2))
          (.put wb ^bytes column-family ^bytes (.nth entry 1)))))
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
