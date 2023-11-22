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
   [blaze.db.impl.codec :as codec]
   [blaze.db.kv :as kv])
  (:import
   [com.google.common.primitives Longs]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn new-iterator
  "Returns the iterator of the system stats index.

  Has to be closed after usage."
  ^AutoCloseable [snapshot]
  (kv/new-iterator snapshot :system-stats-index))

(def ^:private ^:const ^long value-size (+ Long/BYTES Long/BYTES))

(defn- encode-key [t]
  (Longs/toByteArray (codec/descending-long ^long t)))

(defn- decode-value! [buf]
  {:total (bb/get-long! buf)
   :num-changes (bb/get-long! buf)})

(defn get!
  "Returns the value which is most recent according to `t` if there is any.

  Needs to use an iterator because there could be no entry at `t`. So `kv/seek!`
  is used to get near `t`."
  [iter t]
  (kv/seek! iter (encode-key t))
  (when (kv/valid? iter)
    (decode-value! (bb/wrap (kv/value iter)))))

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
