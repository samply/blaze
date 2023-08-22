(ns blaze.db.kv.rocksdb
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.db.kv :as kv]
    [blaze.db.kv.rocksdb.impl :as impl]
    [blaze.db.kv.rocksdb.metrics :as metrics]
    [blaze.db.kv.rocksdb.metrics.spec]
    [blaze.db.kv.rocksdb.protocols :as p]
    [blaze.db.kv.rocksdb.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.lang AutoCloseable]
    [java.nio ByteBuffer]
    [java.util ArrayList]
    [org.rocksdb
     ColumnFamilyHandle CompactRangeOptions Env LRUCache Priority
     ReadOptions RocksDB RocksDBException RocksIterator Snapshot Statistics
     StatsLevel WriteBatch WriteOptions]))


(set! *warn-on-reflection* true)
(RocksDB/loadLibrary)


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

  (-seek-buffer [_ target]
    (.seek i ^ByteBuffer target))

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

  AutoCloseable
  (close [_]
    (.close i)))


(deftype RocksKvSnapshot
  [^RocksDB db ^Snapshot snapshot ^ReadOptions read-opts cfhs]
  kv/KvSnapshot
  (-new-iterator [_]
    (->RocksKvIterator (.newIterator db read-opts)))

  (-new-iterator [_ column-family]
    (->RocksKvIterator (.newIterator db (impl/get-cfh cfhs column-family) read-opts)))

  (-snapshot-get [_ k]
    (.get db read-opts ^bytes k))

  (-snapshot-get [_ column-family k]
    (.get db (impl/get-cfh cfhs column-family) read-opts ^bytes k))

  AutoCloseable
  (close [_]
    (.close read-opts)
    (.releaseSnapshot db snapshot)))


(defn column-families [store]
  (p/-column-families store))


(defn get-property
  ([store name]
   (p/-get-property store name))
  ([store column-family name]
   (p/-get-property store column-family name)))


(defn get-long-property
  ([store name]
   (p/-get-long-property store name))
  ([store column-family name]
   (p/-get-long-property store column-family name)))


(defn table-properties
  ([store]
   (p/-table-properties store))
  ([store column-family]
   (p/-table-properties store column-family)))


(deftype RocksKvStore [^RocksDB db ^WriteOptions write-opts cfhs]
  kv/KvStore
  (-new-snapshot [_]
    (let [snapshot (.getSnapshot db)]
      (->RocksKvSnapshot db snapshot (.setSnapshot (ReadOptions.) snapshot) cfhs)))

  (-get [_ k]
    (.get db k))

  (-get [_ column-family k]
    (.get db (impl/get-cfh cfhs column-family) ^bytes k))

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
      (impl/put-wb! cfhs wb entries)
      (.write db write-opts wb)))

  (-put [_ key value]
    (.put db key value))

  (-delete [_ keys]
    (with-open [wb (WriteBatch.)]
      (impl/delete-wb! wb keys)
      (.write db write-opts wb)))

  (-write [_ entries]
    (with-open [wb (WriteBatch.)]
      (impl/write-wb! cfhs wb entries)
      (.write db write-opts wb)))

  p/Rocks
  (-column-families [_]
    (keys cfhs))

  (-get-property [_ name]
    (try
      (.getProperty db name)
      (catch RocksDBException e
        (impl/property-error e name))))

  (-get-property [_ column-family name]
    (try
      (.getProperty db (impl/get-cfh cfhs column-family) name)
      (catch RocksDBException e
        (impl/column-family-property-error e column-family name))))

  (-get-long-property [_ name]
    (try
      (.getLongProperty db name)
      (catch RocksDBException e
        (impl/property-error e name))))

  (-get-long-property [_ column-family name]
    (try
      (.getLongProperty db (impl/get-cfh cfhs column-family) name)
      (catch RocksDBException e
        (impl/column-family-property-error e column-family name))))

  (-table-properties [_]
    (impl/datafy-tables (.getPropertiesOfAllTables db)))

  (-table-properties [_ column-family]
    (when-ok [cfh (ba/try-anomaly (impl/get-cfh cfhs column-family))]
      (impl/datafy-tables (.getPropertiesOfAllTables db cfh))))

  AutoCloseable
  (close [_]
    (.close db)
    (.close write-opts)))


(defn compact-range!
  "Range compaction of database.

  Note: After the database has been compacted, all data will have been pushed
  down to the last level containing any data."
  ([store]
   (.compactRange ^RocksDB (.db ^RocksKvStore store)))
  ([store column-family change-level target-level]
   (.compactRange
     ^RocksDB (.db ^RocksKvStore store)
     (impl/get-cfh (.cfhs ^RocksKvStore store) column-family)
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


(defmethod ig/pre-init-spec ::kv/rocksdb [_]
  (s/keys :req-un [::dir ::stats] :opt-un [::block-cache ::opts]))


(defn- init-log-msg [dir opts]
  (format "Open RocksDB key-value store in directory `%s` with options: %s. This can take up to several minutes due to forced compaction."
          dir (pr-str opts)))


(defmethod ig/init-key ::kv/rocksdb
  [_ {:keys [dir block-cache stats opts column-families]}]
  (log/info (init-log-msg dir opts))
  (let [cfds (map
               (partial impl/column-family-descriptor block-cache)
               (merge {:default nil} column-families))
        cfhs (ArrayList.)
        db (RocksDB/open (impl/db-options stats opts) dir cfds cfhs)]
    (->RocksKvStore db (impl/write-options opts)
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


(defmethod ig/pre-init-spec ::stats-collector [_]
  (s/keys :req-un [::metrics/stats]))


(defmethod ig/init-key ::stats-collector
  [_ {:keys [stats]}]
  (metrics/stats-collector stats))


(defmethod ig/pre-init-spec ::block-cache-collector [_]
  (s/keys :req-un [::block-cache]))


(defmethod ig/init-key ::block-cache-collector
  [_ {:keys [block-cache]}]
  (metrics/block-cache-collector block-cache))


(defmethod ig/pre-init-spec ::table-reader-collector [_]
  (s/keys :req-un [::metrics/stores]))


(defmethod ig/init-key ::table-reader-collector
  [_ {:keys [stores]}]
  (metrics/table-reader-collector stores))


(derive ::stats-collector :blaze.metrics/collector)
(derive ::block-cache-collector :blaze.metrics/collector)
(derive ::table-reader-collector :blaze.metrics/collector)
