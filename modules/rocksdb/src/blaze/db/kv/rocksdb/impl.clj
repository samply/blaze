(ns blaze.db.kv.rocksdb.impl
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.db.kv.rocksdb.column-family-meta-data :as-alias column-family-meta-data]
   [blaze.db.kv.rocksdb.column-family-meta-data.level :as-alias column-family-meta-data-level]
   [clojure.core.protocols :as p]
   [clojure.string :as str])
  (:import
   [clojure.lang ILookup]
   [com.google.common.base CaseFormat]
   [java.time Instant]
   [java.util List]
   [org.rocksdb
    AbstractEventListener AbstractEventListener$EnabledEventCallback
    BlockBasedTableConfig BloomFilter BuiltinComparator ColumnFamilyDescriptor
    ColumnFamilyHandle ColumnFamilyMetaData ColumnFamilyOptions CompactionJobInfo
    CompactionReason CompressionType DBOptions LevelMetaData RocksDB
    RocksDBException Statistics Status Status$Code TableFileCreationInfo
    TableFileCreationReason TableFileDeletionInfo TableProperties
    WriteBatchInterface WriteOptions]))

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
            merge-operator
            enable-blob-files?
            min-blob-size]
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
          reverse-comparator? false
          enable-blob-files? false
          min-blob-size 0}}]]
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
     (.setMergeOperatorName (name merge-operator))
     enable-blob-files?
     (.setEnableBlobFiles true)
     min-blob-size
     (.setMinBlobSize (long min-blob-size)))))

(defn db-options
  ^DBOptions
  [stats
   listener
   {:keys [wal-dir
           max-background-jobs
           compaction-readahead-size]
    :or {wal-dir ""
         max-background-jobs 2}}]
  (cond-> (doto (DBOptions.)
            (.setWalDir (str wal-dir))
            (.setMaxBackgroundJobs (long max-background-jobs))
            (.setEnablePipelinedWrite true)
            (.setCreateIfMissing true)
            (.setCreateMissingColumnFamilies true)
            (.setListeners ^List (list listener)))
    stats
    (.setStatistics ^Statistics stats)
    (int? compaction-readahead-size)
    (.setCompactionReadaheadSize (long compaction-readahead-size))))

(defn write-options [{:keys [sync? disable-wal?]}]
  (cond-> (WriteOptions.)
    sync?
    (.setSync true)
    disable-wal?
    (.setDisableWAL true)))

(defn- column-family-not-found-msg [column-family]
  (format "Column family `%s` not found." (name column-family)))

(defn get-cfh ^ColumnFamilyHandle [cfhs column-family]
  (let [handle (.valAt ^ILookup cfhs column-family)]
    (if (nil? handle)
      (throw-anom (ba/not-found (column-family-not-found-msg column-family)))
      handle)))

(defn put-wb! [cfhs ^WriteBatchInterface wb entries]
  (run!
   (fn [[column-family key value]]
     (.put wb (get-cfh cfhs column-family) ^bytes key ^bytes value))
   entries))

(defn delete-wb! [cfhs ^WriteBatchInterface wb entries]
  (run!
   (fn [[column-family key]]
     (.delete wb (get-cfh cfhs column-family) ^bytes key))
   entries))

(defn write-wb! [cfhs ^WriteBatchInterface wb entries]
  (run!
   (fn [[op column-family k v]]
     (case op
       :put (.put wb (get-cfh cfhs column-family) ^bytes k ^bytes v)
       :merge (.merge wb (get-cfh cfhs column-family) ^bytes k ^bytes v)
       :delete (.delete wb (get-cfh cfhs column-family) ^bytes k)))
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

(extend-protocol p/Datafiable
  TableProperties
  (datafy [table]
    (cond->
     {:data-size (.getDataSize table)
      :index-size (.getIndexSize table)
      :index-partitions (.getIndexPartitions table)
      :top-level-index-size (.getTopLevelIndexSize table)
      :filter-size (.getFilterSize table)
      :total-raw-key-size (.getRawKeySize table)
      :total-raw-value-size (.getRawValueSize table)
      :num-data-blocks (.getNumDataBlocks table)
      :num-entries (.getNumEntries table)
      :format-version (.getFormatVersion table)
      :fixed-key-len (.getFixedKeyLen table)
      :column-family-id (.getColumnFamilyId table)
      :creation-time (Instant/ofEpochSecond (.getCreationTime table))
      :comparator-name (.getComparatorName table)
      :compression-name (.getCompressionName table)
      :user-collected-properties (.getUserCollectedProperties table)
      :readable-properties (.getReadableProperties table)}
      (pos? (.getOldestKeyTime table))
      (assoc :oldest-key-time (Instant/ofEpochSecond (.getOldestKeyTime table))))))

(defn- datafy-table [[name table]]
  (assoc (p/datafy table) :name name))

(defn datafy-tables [tables]
  (mapv datafy-table tables))

(extend-protocol p/Datafiable
  ColumnFamilyMetaData
  (datafy [meta-data]
    #::column-family-meta-data
     {:name (String. (.name meta-data))
      :file-size (.size meta-data)
      :num-files (.fileCount meta-data)
      :levels (mapv p/datafy (.levels meta-data))})
  LevelMetaData
  (datafy [level-mata-data]
    #::column-family-meta-data-level
     {:level (.level level-mata-data)
      :file-size (.size level-mata-data)
      :num-files (count (.files level-mata-data))}))

(extend-protocol p/Datafiable
  CompactionJobInfo
  (datafy [info]
    {:column-family-name (String. (.columnFamilyName info))
     :status (p/datafy (.status info))
     :thread-id (.threadId info)
     :job-id (.jobId info)
     :base-input-level (.baseInputLevel info)
     :output-level (.outputLevel info)
     :reason (p/datafy (.compactionReason info))
     :compression (p/datafy (.compression info))})
  TableFileCreationInfo
  (datafy [info]
    {:db-name (.getDbName info)
     :column-family-name (.getColumnFamilyName info)
     :file-name (.getFilePath info)
     :job-id (.getJobId info)
     :reason (p/datafy (.getReason info))
     :file-size (.getFileSize info)
     :status (p/datafy (.getStatus info))})
  TableFileDeletionInfo
  (datafy [info]
    {:db-name (.getDbName info)
     :file-name (.getFilePath info)
     :job-id (.getJobId info)
     :status (p/datafy (.getStatus info))})
  Status
  (datafy [status]
    (.getCodeString status))
  CompactionReason
  (datafy [reason]
    (.name reason))
  TableFileCreationReason
  (datafy [reason]
    (.name reason))
  CompressionType
  (datafy [reason]
    (.name reason)))

(defn compaction-begin-msg
  [{:keys [db-name job-id column-family-name base-input-level output-level reason]}]
  (format "Start compaction job %d of column family %s in db %s from level %d to level %d because of %s."
          job-id column-family-name db-name base-input-level output-level
          reason))

(defn compaction-completed-msg
  [{:keys [db-name job-id column-family-name base-input-level output-level reason]}]
  (format "Completed compaction job %d of column family %s in db %s from level %d to level %d because of %s."
          job-id column-family-name db-name base-input-level output-level reason))

(defn table-file-created-msg
  [{:keys [file-name file-size column-family-name job-id reason]}]
  (format "Created SST file %s with size %d for column family %s in job %d because of %s."
          file-name file-size column-family-name job-id reason))

(defn table-file-deleted-msg
  [{:keys [file-name job-id]}]
  (format "Deleted SST file %s in job %d." file-name job-id))

(defn listener
  ^AbstractEventListener
  [& {:keys [on-compaction-begin
             on-compaction-completed
             on-table-file-created
             on-table-file-deleted]
      :or {on-compaction-begin (fn [])
           on-compaction-completed (fn [])
           on-table-file-created (fn [])
           on-table-file-deleted (fn [])}}]
  (proxy [AbstractEventListener]
         [(into-array [AbstractEventListener$EnabledEventCallback/ON_COMPACTION_BEGIN
                       AbstractEventListener$EnabledEventCallback/ON_COMPACTION_COMPLETED
                       AbstractEventListener$EnabledEventCallback/ON_TABLE_FILE_CREATED
                       AbstractEventListener$EnabledEventCallback/ON_TABLE_FILE_DELETED])]
    (onCompactionBegin [^RocksDB db ^CompactionJobInfo info]
      (try
        (on-compaction-begin (assoc (p/datafy info) :db-name (.getName db)))
        (finally
          (.close info))))
    (onCompactionCompleted [^RocksDB db ^CompactionJobInfo info]
      (try
        (on-compaction-completed (assoc (p/datafy info) :db-name (.getName db)))
        (finally
          (.close info))))
    (onTableFileCreated [info]
      (on-table-file-created (p/datafy info)))
    (onTableFileDeleted [info]
      (on-table-file-deleted (p/datafy info)))))

(defn- snake->kebab [s]
  (.to CaseFormat/LOWER_UNDERSCORE CaseFormat/LOWER_HYPHEN s))

(defn map-property [m]
  (reduce-kv
   #(let [key (mapv keyword (str/split (snake->kebab %2) #"\."))]
      (assoc-in %1 key %3))
   {}
   m))
