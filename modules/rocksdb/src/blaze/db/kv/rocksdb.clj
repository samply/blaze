(ns blaze.db.kv.rocksdb
  (:require
    [blaze.db.kv :as kv]
    [blaze.db.kv.rocksdb.impl :as impl]
    [blaze.db.kv.rocksdb.metrics :as metrics]
    [blaze.db.kv.rocksdb.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [blaze.db.kv KvIterator]
    [java.lang AutoCloseable]
    [java.util ArrayList EnumSet]
    [org.rocksdb
     RocksDB RocksIterator WriteOptions WriteBatch Options ColumnFamilyHandle
     DBOptions Statistics LRUCache CompactRangeOptions Snapshot ReadOptions
     StatsLevel HistogramType]))


(set! *warn-on-reflection* true)


(deftype RocksKvIterator [^RocksIterator i]
  KvIterator
  (valid [_]
    (.isValid i))

  (seekToFirst [_]
    (.seekToFirst i))

  (seekToLast [_]
    (.seekToLast i))

  (seek [_ target]
    (.seek i target))

  (seekBuffer [_ target]
    (.seek i target))

  (seekForPrev [_ target]
    (.seekForPrev i target))

  (next [_]
    (.next i))

  (prev [_]
    (.prev i))

  (key [_]
    (.key i))

  (key [_ buf]
    (.key i buf))

  (value [_]
    (.value i))

  (value [_ buf]
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


(defprotocol Rocks
  (-get-property [_ name] [_ column-family name]))


(defn get-property
  ([store name]
   (-get-property store name))
  ([store column-family name]
   (-get-property store column-family name)))


(deftype RocksKvStore [^RocksDB db ^Options opts ^WriteOptions write-opts cfhs]
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

  Rocks
  (-get-property [_ name]
    (.getProperty db name))

  (-get-property [_ column-family name]
    (.getProperty db (impl/get-cfh cfhs column-family) name))

  AutoCloseable
  (close [_]
    (.close db)
    (.close opts)
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
  (RocksDB/loadLibrary)
  (LRUCache. (bit-shift-left size-in-mb 20)))


(defmethod ig/halt-key! ::block-cache
  [_ cache]
  (log/info "Shutdown RocksDB block cache")
  (.close ^AutoCloseable cache))


(defmethod ig/pre-init-spec ::kv/rocksdb [_]
  (s/keys :req-un [::dir ::block-cache ::stats]))


(defn- init-log-msg [dir opts]
  (format "Open RocksDB key-value store in directory `%s` with options: %s. This can take up to several minutes due to forced compaction."
          dir (pr-str opts)))


(defmethod ig/init-key ::kv/rocksdb
  [_ {:keys [dir block-cache stats opts column-families]}]
  (log/info (init-log-msg dir opts))
  (let [^DBOptions db-options (impl/db-options stats opts)
        cfds (map
               (partial impl/column-family-descriptor block-cache)
               (merge {:default nil} column-families))
        cfhs (ArrayList.)
        db (try
             (RocksDB/open db-options dir cfds cfhs)
             (finally (.close db-options)))]
    (->RocksKvStore db db-options (impl/write-options opts)
                    (index-column-family-handles cfhs))))


(defmethod ig/halt-key! ::kv/rocksdb
  [_ store]
  (log/info "Close RocksDB key-value store")
  (.close ^AutoCloseable store))


(defmethod ig/init-key ::stats
  [_ _]
  (log/info "Init RocksDB statistics")
  (doto (Statistics. (EnumSet/allOf HistogramType))
    (.setStatsLevel StatsLevel/EXCEPT_DETAILED_TIMERS)))


(defmethod ig/halt-key! ::stats
  [_ stats]
  (log/info "Shutdown RocksDB statistics")
  (.close ^AutoCloseable stats))


(defmethod ig/init-key ::stats-collector
  [_ {:keys [stats]}]
  (metrics/stats-collector stats))


(derive ::stats-collector :blaze.metrics/collector)
