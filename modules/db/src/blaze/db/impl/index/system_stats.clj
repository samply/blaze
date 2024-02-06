(ns blaze.db.impl.index.system-stats
  "The SystemStats index is used to track the total number of resources and the
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
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.iterators :as i])
  (:import
   [com.google.common.primitives Longs]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long value-size (+ Long/BYTES Long/BYTES))

(defn encode-key [t]
  (Longs/toByteArray (codec/descending-long ^long t)))

(defn- decode-value! [buf]
  {:total (bb/get-long! buf)
   :num-changes (bb/get-long! buf)})

(defn seek-value
  "Returns the value which is most recent according to `t` if there is any."
  [snapshot t]
  (i/seek-value snapshot :system-stats-index decode-value! 0
                (bs/from-byte-array (encode-key t))))

(defn- encode-value [{:keys [total num-changes]}]
  (-> (bb/allocate value-size)
      (bb/put-long! total)
      (bb/put-long! num-changes)
      bb/array))

(defn index-entry
  "Returns an entry of the SystemStats index build from `t` and `value`.

  The value is a map of :total and :num-changes."
  [t value]
  [:system-stats-index (encode-key t) (encode-value value)])
