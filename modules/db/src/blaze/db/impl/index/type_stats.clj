(ns blaze.db.impl.index.type-stats
  "The type stats index is used to track the total number of resources and the
  number of changes to resources of a particular type.

  The total value is used in the search-type interaction as total value of the
  bundle if no search parameters were supplied.

  The number of changes value is used in the history-type interaction as total
  value of the bundle.

  The key used contains the tid (hash of the type) and the t of the transaction.
  The value contains two long values, the first for the total and the second for
  the number of changes.

  Each transaction which touches resources of a particular type, puts an entry
  with the new totals for this type at its t."
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.kv :as kv])
  (:import
    [java.io Closeable]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn new-iterator
  "Returns the iterator of the type stats index.

  Has to be closed after usage."
  ^Closeable [snapshot]
  (kv/new-iterator snapshot :type-stats-index))


(defn- encode-key [tid t]
  (-> (ByteBuffer/allocate (+ codec/tid-size codec/t-size))
      (.putInt tid)
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
  [tid t value]
  [:type-stats-index (encode-key tid t) (encode-value value)])


(defn- key->tid [k]
  (.getInt (ByteBuffer/wrap k)))


(defn- decode-value [v]
  (let [bb (ByteBuffer/wrap v)]
    {:total (.getLong bb)
     :num-changes (.getLong bb 8)}))


(defn get!
  "Returns the value of `tid` which is most recent according to `t` if there is
  any.

  Needs to use an iterator because there could be no entry at `t`. So `kv/seek!`
  is used to get near `t`."
  [iter tid t]
  (kv/seek! iter (encode-key tid t))
  (when (kv/valid? iter)
    (when (= tid (key->tid (kv/key iter)))
      (decode-value (kv/value iter)))))
