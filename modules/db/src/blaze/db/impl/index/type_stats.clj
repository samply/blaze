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
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.iterators :as i]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long key-size (+ codec/tid-size codec/t-size))
(def ^:private ^:const ^long value-size (+ Long/BYTES Long/BYTES))

(defn- encode-key [tid t]
  (-> (bb/allocate key-size)
      (bb/put-int! tid)
      (bb/put-long! (codec/descending-long ^long t))
      bb/array))

(defn- decode-value! [buf]
  {:total (bb/get-long! buf)
   :num-changes (bb/get-long! buf)})

(defn seek-value
  "Returns the value of `tid,` which is most recent, according to `t,` if there is
  any."
  [snapshot tid t]
  (i/seek-value snapshot :type-stats-index decode-value! codec/tid-size
                (bs/from-byte-array (encode-key tid t))))

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
