(ns blaze.db.node.stats
  "In-memory head values of the TypeStats and SystemStats indexes.

  Both indexes are maintained by the transaction indexer alone and it applies
  the transactions of a node strictly in order on a single thread. So the entry
  it would seek at the t of the database before a transaction is always exactly
  the one it wrote for the previous transaction. Holding those values in memory
  removes that seek from the indexing loop.

  The index entries themselves are still written, because database values read
  them at arbitrary t for the totals of the search and history interactions.
  Only the read of the head value moves into memory."
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.kv :as kv]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private empty-value
  {:total 0 :num-changes 0})

(def empty-stats
  "Stats of a database without any transaction."
  {:types {} :system empty-value})

(defn- add
  "Adds the `increments` of one type of a transaction to `value`.

  The increments are partial, because a transaction can change the number of
  changes of a type without changing its total and vice versa."
  [value increments]
  {:total (+ (long (:total value)) (long (:total increments 0)))
   :num-changes (+ (long (:num-changes value))
                   (long (:num-changes increments 0)))})

(defn- type-value [snapshot t type]
  (let [tid (codec/tid type)]
    (when-let [value (type-stats/seek-value snapshot tid t)]
      [tid value])))

(defn init
  "Returns the stats of the TypeStats and SystemStats indexes of `kv-store` at
  `t`.

  Seeks one entry per resource type and one for the system-wide totals, so this
  is meant to be called once while the node starts."
  [kv-store t]
  (with-open [snapshot (kv/new-snapshot kv-store)]
    {:types (into {} (keep (partial type-value snapshot t))
                  codec/all-types)
     :system (or (system-stats/seek-value snapshot t) empty-value)}))

(defn apply-tx
  "Applies the `increments` of the transaction with `t` to `stats`.

  The `increments` are a map of tid to the partial increments of that type,
  exactly as the verification of the transaction commands accumulates them.

  Returns a tuple of the TypeStats and SystemStats index entries of that
  transaction and the stats after it. Returns no entries at all if the
  transaction changed nothing, because the indexes only carry entries at the t
  of a transaction that touched resources."
  [stats t increments]
  (if (seq increments)
    (loop [entries (transient [])
           types (:types stats)
           system (:system stats)
           increments (seq increments)]
      (if-let [[tid increment] (first increments)]
        (let [value (add (types tid empty-value) increment)]
          (recur (conj! entries (type-stats/index-entry tid t value))
                 (assoc types tid value)
                 (add system increment)
                 (next increments)))
        [(persistent! (conj! entries (system-stats/index-entry t system)))
         {:types types :system system}]))
    [[] stats]))
