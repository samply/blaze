(ns blaze.db.kv.rocksdb
  (:refer-clojure :exclude [range])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.db.kv :as kv]
   [blaze.db.kv.protocols :as kv-p]
   [blaze.db.kv.rocksdb.impl :as impl]
   [blaze.db.kv.rocksdb.metrics :as metrics]
   [blaze.db.kv.rocksdb.metrics.spec]
   [blaze.db.kv.rocksdb.protocols :as p]
   [blaze.db.kv.rocksdb.spec]
   [blaze.module :as m]
   [clojure.datafy :as datafy]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [java.lang AutoCloseable]
   [java.nio ByteBuffer]
   [java.util ArrayList]
   [org.rocksdb
    ColumnFamilyHandle Env LRUCache Priority Range ReadOptions RocksDB
    RocksDBException RocksIterator SizeApproximationFlag Slice Snapshot Statistics StatsLevel WriteBatch
    WriteOptions]))

(set! *warn-on-reflection* true)
(RocksDB/loadLibrary)

(deftype RocksKvIterator [^RocksIterator i]
  kv-p/KvIterator
  (-valid [_]
    (.isValid i))

  (-seek-to-first [_]
    (.seekToFirst i))

  (-seek-to-last [_]
    (.seekToLast i))

  (-seek [_ target]
    (.seek i ^bytes target))

  (-seek-buffer [_ target]
    (.seek i ^ByteBuffer target))

  (-seek-for-prev [_ target]
    (.seekForPrev i ^bytes target))

  (-seek-for-prev-buffer [_ target]
    (.seekForPrev i ^ByteBuffer target))

  (-next [_]
    (.next i))

  (-prev [_]
    (.prev i))

  (-key [_]
    (.key i))

  (-key [_ buf]
    (.key i ^ByteBuffer buf))

  (-value [_]
    (.value i))

  (-value [_ buf]
    (.value i ^ByteBuffer buf))

  AutoCloseable
  (close [_]
    (.close i)))

(deftype RocksKvSnapshot [^RocksDB db ^Snapshot snapshot ^ReadOptions read-opts cfhs]
  kv-p/KvSnapshot
  (-new-iterator [_ column-family]
    (->RocksKvIterator (.newIterator db (impl/get-cfh cfhs column-family) read-opts)))

  (-snapshot-get [_ column-family k]
    (.get db (impl/get-cfh cfhs column-family) read-opts ^bytes k))

  AutoCloseable
  (close [_]
    (.close read-opts)
    (.releaseSnapshot db snapshot)))

(defn path
  "Returns the file system path `store` uses."
  [store]
  (p/-path store))

(defn column-families
  "Returns a sorted seq of column family names available in `store` as keywords."
  [store]
  (p/-column-families store))

(defn property
  "Returns the string value of the property with `name` from `store`.

  Additionally an optional `column-family` key can be supplied in order to
  obtain the value of a column family specific property.

  Returns an anomaly if the property was not found or the optional column family
  doesn't exist."
  ([store name]
   (p/-property store name))
  ([store column-family name]
   (p/-property store column-family name)))

(defn long-property
  "Returns the long value of the property with `name` from `store`.

  Additionally an optional `column-family` key can be supplied in order to
  obtain the value of a column family specific property.

  Returns an anomaly if the property was not found or the optional column family
  doesn't exist."
  ([store name]
   (p/-long-property store name))
  ([store column-family name]
   (p/-long-property store column-family name)))

(defn agg-long-property
  "Returns the aggregated long value of the property with `name` from `store`.

  Returns an anomaly if the property was not found."
  [store name]
  (p/-agg-long-property store name))

(defn map-property
  "Returns the map value of the property with `name` from `store`.

  Additionally an optional `column-family` key can be supplied in order to
  obtain the value of a column family specific property.

  Returns an anomaly if the property was not found or the optional column family
  doesn't exist."
  ([store name]
   (p/-map-property store name))
  ([store column-family name]
   (p/-map-property store column-family name)))

(defn tables
  "Returns a coll of tables used by `store`.

  Additionally an optional `column-family` key can be supplied in order to
  obtain the table of a column family.

  Returns an anomaly if the optional column family doesn't exist."
  ([store]
   (p/-tables store))
  ([store column-family]
   (p/-tables store column-family)))

(defn column-family-meta-data
  "Returns the metadata of `column-family` of `store`.

  Returns an anomaly if the column family doesn't exist."
  [store column-family]
  (p/-column-family-meta-data store column-family))

(defn drop-column-family!
  "Drops `column-family` in `store`.

  This call only records a drop record in the manifest and prevents the column
  family from flushing and compacting.

  The column family data will be removed from disk after `store` is closed.

  Returns an anomaly if the column family doesn't exist."
  [store column-family]
  (p/-drop-column-family store column-family))

(defn- range [[start end]]
  (Range. (Slice. ^bytes (bs/to-byte-array start))
          (Slice. ^bytes (bs/to-byte-array end))))

(def ^:private approximation-flags
  (doto ^objects (make-array SizeApproximationFlag 2)
    (aset 0 SizeApproximationFlag/INCLUDE_FILES)
    (aset 1 SizeApproximationFlag/INCLUDE_MEMTABLES)))

(defn- estimate-scan-size [^RocksDB db cfh key-range]
  (-> (.getApproximateSizes db cfh [(range key-range)] approximation-flags)
      (aget 0)))

(deftype RocksKvStore [^RocksDB db path ^WriteOptions write-opts cfhs]
  kv-p/KvStore
  (-new-snapshot [_]
    (let [snapshot (.getSnapshot db)]
      (->RocksKvSnapshot db snapshot (.setSnapshot (ReadOptions.) snapshot) cfhs)))

  (-get [_ column-family k]
    (.get db (impl/get-cfh cfhs column-family) ^bytes k))

  (-put [_ entries]
    (with-open [wb (WriteBatch.)]
      (impl/put-wb! cfhs wb entries)
      (.write db write-opts wb)))

  (-delete [_ entries]
    (with-open [wb (WriteBatch.)]
      (impl/delete-wb! cfhs wb entries)
      (.write db write-opts wb)))

  (-write [_ entries]
    (with-open [wb (WriteBatch.)]
      (impl/write-wb! cfhs wb entries)
      (.write db write-opts wb)))

  (-estimate-num-keys [store column-family]
    (p/-long-property store column-family "rocksdb.estimate-num-keys"))

  (-estimate-scan-size [_ column-family key-range]
    (when-ok [cfh (ba/try-anomaly (impl/get-cfh cfhs column-family))]
      (estimate-scan-size db cfh key-range)))

  ;; After the column family has been compacted, all data will have been pushed
  ;; down to the last level containing any data.
  (-compact [_ column-family]
    (if-ok [cfh (ba/try-anomaly (impl/get-cfh cfhs column-family))]
      (ac/supply-async #(.compactRange db cfh))
      ac/completed-future))

  p/Rocks
  (-path [_]
    path)

  (-column-families [_]
    (sort (keys cfhs)))

  (-property [_ name]
    (try
      (.getProperty db name)
      (catch RocksDBException e
        (impl/property-error e name))))

  (-property [_ column-family name]
    (when-ok [cfh (ba/try-anomaly (impl/get-cfh cfhs column-family))]
      (try
        (.getProperty db cfh name)
        (catch RocksDBException e
          (impl/column-family-property-error e column-family name)))))

  (-long-property [_ name]
    (try
      (.getLongProperty db name)
      (catch RocksDBException e
        (impl/property-error e name))))

  (-long-property [_ column-family name]
    (when-ok [cfh (ba/try-anomaly (impl/get-cfh cfhs column-family))]
      (try
        (.getLongProperty db cfh name)
        (catch RocksDBException e
          (impl/column-family-property-error e column-family name)))))

  (-agg-long-property [_ name]
    (try
      (.getAggregatedLongProperty db name)
      (catch RocksDBException e
        (impl/property-error e name))))

  (-map-property [_ name]
    (try
      (impl/map-property (.getMapProperty db name))
      (catch RocksDBException e
        (impl/property-error e name))))

  (-map-property [_ column-family name]
    (when-ok [cfh (ba/try-anomaly (impl/get-cfh cfhs column-family))]
      (try
        (impl/map-property (.getMapProperty db cfh name))
        (catch RocksDBException e
          (impl/column-family-property-error e column-family name)))))

  (-tables [_]
    (impl/datafy-tables (.getPropertiesOfAllTables db)))

  (-tables [_ column-family]
    (when-ok [cfh (ba/try-anomaly (impl/get-cfh cfhs column-family))]
      (impl/datafy-tables (.getPropertiesOfAllTables db cfh))))

  (-column-family-meta-data [_ column-family]
    (when-ok [cfh (ba/try-anomaly (impl/get-cfh cfhs column-family))]
      (datafy/datafy (.getColumnFamilyMetaData db cfh))))

  (-drop-column-family [_ column-family]
    (when-ok [cfh (ba/try-anomaly (impl/get-cfh cfhs column-family))]
      (.dropColumnFamily db cfh)))

  AutoCloseable
  (close [_]
    (.close db)
    (.close write-opts)))

(defn- cfh-key [cfh]
  (keyword (String. (.getName ^ColumnFamilyHandle cfh))))

(defn- index-column-family-handles [column-family-handles]
  (reduce #(assoc %1 (cfh-key %2) %2) {} column-family-handles))

(defmethod ig/init-key ::block-cache
  [_ {:keys [size-in-mb] :or {size-in-mb 128}}]
  (log/info (format "Init RocksDB block cache of %d MB" size-in-mb))
  (LRUCache. (bit-shift-left size-in-mb 20)))

(defmethod ig/halt-key! ::block-cache
  [_ cache]
  (log/info "Shutdown RocksDB block cache")
  (.close ^AutoCloseable cache))

(defmethod ig/init-key ::env
  [_ _]
  (log/info (format "Init RocksDB environment"))
  (doto (Env/getDefault)
    (.setBackgroundThreads 2 Priority/HIGH)
    (.setBackgroundThreads 6 Priority/LOW)))

(defmethod m/pre-init-spec ::kv/rocksdb [_]
  (s/keys :req-un [::dir]
          :opt-un [::block-cache ::stats ::opts ::column-families]))

(defn- init-log-msg [dir opts]
  (format "Open RocksDB key-value store in directory `%s` with options: %s. This can take up to several minutes due to forced compaction."
          dir (pr-str opts)))

(defn listener []
  (impl/listener
   :on-compaction-begin #(log/debug (impl/compaction-begin-msg %))
   :on-compaction-completed #(log/debug (impl/compaction-completed-msg %))
   :on-table-file-created #(log/debug (impl/table-file-created-msg %))
   :on-table-file-deleted #(log/debug (impl/table-file-deleted-msg %))))

(defmethod ig/init-key ::kv/rocksdb
  [_ {:keys [dir block-cache stats opts column-families]}]
  (log/info (init-log-msg dir opts))
  (let [cfds (map
              (partial impl/column-family-descriptor block-cache)
              (merge {:default nil} column-families))
        cfhs (ArrayList.)
        db (RocksDB/open (impl/db-options stats (listener) opts) dir cfds cfhs)]
    (->RocksKvStore db dir (impl/write-options opts)
                    (index-column-family-handles cfhs))))

(defmethod ig/halt-key! ::kv/rocksdb
  [_ store]
  (log/info "Close RocksDB key-value store")
  (.close ^AutoCloseable store))

(defmethod ig/init-key ::stats
  [_ _]
  (log/info "Init RocksDB statistics")
  (doto (Statistics.)
    (.setStatsLevel StatsLevel/EXCEPT_TIME_FOR_MUTEX)))

(defmethod ig/halt-key! ::stats
  [_ stats]
  (log/info "Shutdown RocksDB statistics")
  (.close ^AutoCloseable stats))

(defmethod m/pre-init-spec ::stats-collector [_]
  (s/keys :req-un [::metrics/stats]))

(defmethod ig/init-key ::stats-collector
  [_ {:keys [stats]}]
  (metrics/stats-collector stats))

(defmethod m/pre-init-spec ::block-cache-collector [_]
  (s/keys :req-un [::block-cache]))

(defmethod ig/init-key ::block-cache-collector
  [_ {:keys [block-cache]}]
  (metrics/block-cache-collector block-cache))

(defmethod m/pre-init-spec ::table-reader-collector [_]
  (s/keys :req-un [::metrics/stores]))

(defmethod ig/init-key ::table-reader-collector
  [_ {:keys [stores]}]
  (metrics/table-reader-collector stores))

(derive ::stats-collector :blaze.metrics/collector)
(derive ::block-cache-collector :blaze.metrics/collector)
(derive ::table-reader-collector :blaze.metrics/collector)
