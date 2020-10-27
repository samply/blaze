(ns blaze.db.impl.index.system-stats
  "The system stats index is used to track the total number of resources and the
  number of changes to resources in the whole system.

  The total value is used in the search-system interaction as total value of the
  bundle if no search parameters were supplied.

  The number of changes value is used in the history-system interaction as total
  value of the bundle.

  The key used contains the t of the transaction. The value contains two long
  values, the first for the total and the second for the number of changes.

  Each transaction which touches any resources, puts an entry with the new
  totals at its t."
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.kv :as kv])
  (:import
    [java.io Closeable]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn new-iterator
  "Returns the iterator of the system stats index.

  Has to be closed after usage."
  ^Closeable [snapshot]
  (kv/new-iterator snapshot :system-stats-index))


(defn- encode-key [t]
  (-> (ByteBuffer/allocate codec/t-size)
      (.putLong (codec/descending-long t))
      (.array)))


(defn- encode-value [{:keys [total num-changes]}]
  (-> (ByteBuffer/allocate 16)
      (.putLong total)
      (.putLong num-changes)
      (.array)))


(defn entry
  "Creates an entry which can be written to the key-value store.

  The value is a map of :total and :num-changes."
  [t value]
  [:system-stats-index (encode-key t) (encode-value value)])


(defn- decode-value [v]
  (let [bb (ByteBuffer/wrap v)]
    {:total (.getLong bb)
     :num-changes (.getLong bb 8)}))


(defn get!
  "Returns the value which is most recent according to `t` if there is any.

  Needs to use an iterator because there could be no entry at `t`. So `kv/seek!`
  is used to get near `t`."
  [iter t]
  (kv/seek! iter (encode-key t))
  (when (kv/valid? iter)
    (decode-value (kv/value iter))))
