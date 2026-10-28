(ns blaze.db.kv.iter-pool
  "This namespace provides a `pooling-snapshot` that pools iterators instead of
  opening and closing them every time a new iterator is requested.

  An iterator pool is useful because opening and closing a RocksDB iterator is
  quite expensive.

  Instead of opening a new iterator, available, existing iterators are borrowed
  from the pool. Instead of closing an iterator, it will be returned to the
  pool. Iterators returned to the pool will never expire because snapshots
  itself are short-lived and all iterators are closed when the snapshot is
  closed.

  Each thread using a snapshot has its own pool, so a snapshot can be used from
  several threads at once without contention. A pool holds, per column family,
  a deque of returned iterators, plus all iterators it created. It is guarded by
  its own lock, which is uncontended, because a pool is almost always used only
  by its own thread. Iterators are borrowed exclusively and only handed over
  sequentially. A pooled iterator is returned to the pool it was borrowed from,
  even if the returning code runs on another thread. Closing a pooled iterator
  more than once, even from several threads at once, returns it exactly once.

  Closing a snapshot closes all its iterators, borrowed and returned, and then
  the underlying snapshot before it returns. So the native resources of a
  snapshot never outlive its close, even if the code borrowing an iterator
  never closed it. Borrowing an iterator after close throws a fault, and so
  does using a borrowed iterator after close, because a pooled iterator checks
  whether its pool is closed before each use. Returning an iterator after close
  has no effect. The Java classes `Pools` and `Pool` document the details.

  That check can't prevent a race: a thread that passed it while another thread
  closes the snapshot calls RocksDB on a freed native handle, which can crash
  the JVM. So a snapshot must only be closed after all work using it is done. No
  future using a snapshot may outlive the scope that closes it."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.db.kv.protocols :as p])
  (:import
   [blaze.db.kv KvCloseable]
   [blaze.db.kv.iter_pool Pool Pools]))

(set! *warn-on-reflection* true)

(def ^:private iterator-closed-anom
  (ba/fault "The iterator is closed."))

(def ^:private snapshot-closed-anom
  (ba/fault "The snapshot is closed."))

(defmacro ^:private check-open
  "Throws a fault if the pooled iterator or the pool it was borrowed from is
  closed."
  [closed? pool]
  `(do (when ~closed? (throw-anom iterator-closed-anom))
       (when (.isClosed ~pool) (throw-anom snapshot-closed-anom))))

;; closed? is only written under the lock of the pool, so that the iterator is
;; returned exactly once, even if it is closed from several threads at once. It
;; is read without lock only to detect use after close by the code holding the
;; iterator, which uses it from one thread at a time, so it doesn't need to be
;; volatile.
(deftype PooledIterator [iter ^Pool pool column-family ^:unsynchronized-mutable ^boolean closed?]
  p/KvIterator
  (-valid [_]
    (check-open closed? pool)
    (p/-valid iter))

  (-seek-to-first [_]
    (check-open closed? pool)
    (p/-seek-to-first iter))

  (-seek-to-last [_]
    (check-open closed? pool)
    (p/-seek-to-last iter))

  (-seek [_ target]
    (check-open closed? pool)
    (p/-seek iter target))

  (-seek-buffer [_ target]
    (check-open closed? pool)
    (p/-seek-buffer iter target))

  (-seek-for-prev [_ target]
    (check-open closed? pool)
    (p/-seek-for-prev iter target))

  (-seek-for-prev-buffer [_ target]
    (check-open closed? pool)
    (p/-seek-for-prev-buffer iter target))

  (-next [_]
    (check-open closed? pool)
    (p/-next iter))

  (-prev [_]
    (check-open closed? pool)
    (p/-prev iter))

  (-key [_]
    (check-open closed? pool)
    (p/-key iter))

  (-key [_ buf]
    (check-open closed? pool)
    (p/-key iter buf))

  (-value [_]
    (check-open closed? pool)
    (p/-value iter))

  (-value [_ buf]
    (check-open closed? pool)
    (p/-value iter buf))

  KvCloseable
  (close [_]
    ;; returning the iterator twice would hand it out to two borrowers
    (locking pool
      (when-not closed?
        (set! closed? (boolean true))
        (.giveBack pool column-family iter)))))

(deftype PoolingSnapshot [^Pools pools]
  p/KvSnapshot
  (-new-iterator [_ column-family]
    (let [pool (.pool pools)]
      (if-let [iter (some-> pool (.borrow column-family))]
        (->PooledIterator iter pool column-family false)
        (throw-anom snapshot-closed-anom))))

  KvCloseable
  (close [_]
    (.close pools)))

(defn pooling-snapshot
  "Returns a snapshot that pools the iterators of `snapshot`."
  ^KvCloseable [snapshot]
  (->PoolingSnapshot (Pools. snapshot p/-new-iterator)))
