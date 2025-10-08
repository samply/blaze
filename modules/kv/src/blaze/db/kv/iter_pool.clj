(ns blaze.db.kv.iter-pool
  "This namespace provides a `pooling-snapshot` that pools iterators instead of
  opening and closing them every time a new iterator is requested.

  An iterator pool is useful because opening and closing a RocksDB iterator is
  quite expensive.

  Instead of opening a new iterator, available, existing iterators are borrowed
  from the pool. Instead of closing an iterator, it will be returned to the
  pool. Iterators returned to the pool will never expire because snapshots
  itself are short-lived and iterators are closed when the snapshot is closed.

  The pool is implemented by using a ConcurrentHashMap that holds a state for
  each column family. The state is implemented in Java as mutable class holding
  a deque of returned iterators. The iterators will be removed from that queue
  if borrowed and added again if returned.

  The initial state is empty. State transitions are documented as JavaDoc at the
  State class.

  Borrowing an iterator including state transition and possibly creating a new
  iterator from the original snapshot is implemented in the `-new-iterator`
  protocol method of PoolingSnapshot. It first carries out a state transition of
  borrowing an iterator. If no iterator is available, it will create a new
  iterator, carries out the state transition to add it into the state and
  returns it.

  Returning an iterator is implemented in the `close` method of PooledIterator."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.db.kv.protocols :as p])
  (:import
   [blaze.db.kv.iter_pool State]
   [java.lang AutoCloseable]
   [java.util.concurrent ConcurrentHashMap]))

(set! *warn-on-reflection* true)

(def ^:private iterator-closed-anom
  (ba/fault "The iterator is closed."))

(deftype PooledIterator [iter pool column-family ^:volatile-mutable closed?]
  p/KvIterator
  (-valid [_]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-valid iter))

  (-seek-to-first [_]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-seek-to-first iter))

  (-seek-to-last [_]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-seek-to-last iter))

  (-seek [_ target]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-seek iter target))

  (-seek-buffer [_ target]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-seek-buffer iter target))

  (-seek-for-prev [_ target]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-seek-for-prev iter target))

  (-seek-for-prev-buffer [_ target]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-seek-for-prev-buffer iter target))

  (-next [_]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-next iter))

  (-prev [_]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-prev iter))

  (-key [_]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-key iter))

  (-key [_ buf]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-key iter buf))

  (-value [_]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-value iter))

  (-value [_ buf]
    (when closed? (throw-anom iterator-closed-anom))
    (p/-value iter buf))

  AutoCloseable
  (close [_]
    (set! closed? true)
    (State/returnIterator pool column-family iter)))

(deftype PoolingSnapshot [snapshot pool]
  p/KvSnapshot
  (-new-iterator [_ column-family]
    (-> (or (State/borrowIterator pool column-family)
            (let [iter (p/-new-iterator snapshot column-family)]
              (State/addIterator pool column-family iter)
              iter))
        (->PooledIterator pool column-family false)))

  (-snapshot-get [_ column-family key]
    (p/-snapshot-get snapshot column-family key))

  AutoCloseable
  (close [_]
    (State/closeAllIterators pool)
    (.close ^AutoCloseable snapshot)))

(defn pooling-snapshot ^AutoCloseable [snapshot]
  (->PoolingSnapshot snapshot (ConcurrentHashMap.)))
