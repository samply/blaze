(ns blaze.db.impl.index.type-stats
  "The TypeStats index is used to track the total number of resources and the
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
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.kv :as kv])
  (:import
    [java.lang AutoCloseable]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn new-iterator
  "Returns the iterator of the type stats index.

  Has to be closed after usage."
  ^AutoCloseable [snapshot]
  (kv/new-iterator snapshot :type-stats-index))


(def ^:private ^:const ^long key-size (+ codec/tid-size codec/t-size))
(def ^:private ^:const ^long value-size (+ Long/BYTES Long/BYTES))
(def ^:private ^:const ^long kv-capacity (max key-size value-size))


(defn- decode-value! [buf]
  {:total (bb/get-long! buf)
   :num-changes (bb/get-long! buf)})


(defn get!
  "Returns the value of `tid` which is most recent according to `t` if there is
  any.

  Needs to use an iterator because there could be no entry at `t`. So `kv/seek!`
  is used to get near `t`."
  [iter tid t]
  (let [buf (bb/allocate-direct kv-capacity)]
    (bb/put-int! buf tid)
    (bb/put-long! buf (codec/descending-long ^long t))
    (bb/flip! buf)
    (kv/seek-buffer! iter buf)
    (when (kv/valid? iter)
      (bb/clear! buf)
      (kv/key! iter buf)
      (when (= ^long tid (bb/get-int! buf))
        (bb/clear! buf)
        (kv/value! iter buf)
        (decode-value! buf)))))


(defn- encode-key [tid t]
  (-> (bb/allocate key-size)
      (bb/put-int! tid)
      (bb/put-long! (codec/descending-long ^long t))
      bb/array))


(defn- encode-value [{:keys [total num-changes]}]
  (-> (bb/allocate value-size)
      (bb/put-long! total)
      (bb/put-long! num-changes)
      bb/array))


(defn index-entry
  "Returns an entry of the TypeStats index build from `tid`, `t` and `value`.

  The value is a map of :total and :num-changes."
  [tid t value]
  [:type-stats-index (encode-key tid t) (encode-value value)])
