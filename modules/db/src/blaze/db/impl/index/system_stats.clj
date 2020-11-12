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
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.kv :as kv])
  (:import
    [java.io Closeable]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn new-iterator
  "Returns the iterator of the system stats index.

  Has to be closed after usage."
  ^Closeable [snapshot]
  (kv/new-iterator snapshot :system-stats-index))


(def ^:private ^:const ^long key-size codec/t-size)
(def ^:private ^:const ^long value-size (+ Long/BYTES Long/BYTES))
(def ^:private ^:const ^long kv-capacity (max key-size value-size))


(defn- decode-value! [buf]
  {:total (bb/get-long! buf)
   :num-changes (bb/get-long! buf)})


(defn get!
  "Returns the value which is most recent according to `t` if there is any.

  Needs to use an iterator because there could be no entry at `t`. So `kv/seek!`
  is used to get near `t`."
  [iter t]
  (let [buf (bb/allocate-direct kv-capacity)]
    (bb/put-long! buf (codec/descending-long ^long t))
    (bb/flip! buf)
    (kv/seek-buffer! iter buf)
    (when (kv/valid? iter)
      (bb/clear! buf)
      (kv/value! iter buf)
      (decode-value! buf))))


(defn- encode-key [t]
  (-> (bb/allocate key-size)
      (bb/put-long! (codec/descending-long ^long t))
      (bb/array)))


(defn- encode-value [{:keys [total num-changes]}]
  (-> (bb/allocate value-size)
      (bb/put-long! total)
      (bb/put-long! num-changes)
      (bb/array)))


(defn index-entry
  "Creates an entry which can be written to the key-value store.

  The value is a map of :total and :num-changes."
  [t value]
  [:system-stats-index (encode-key t) (encode-value value)])
