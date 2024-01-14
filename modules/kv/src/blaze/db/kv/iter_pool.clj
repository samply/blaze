(ns blaze.db.kv.iter-pool
  "This namespace provides a `pooling-snapshot` that pools iterators instead of
  opening and closing them every time a new iterator is requested.

  An iterator pool is useful because opening and closing a RocksDB iterator is
  quite expensive.

  Instead of opening a new iterator, available, existing iterators are borrowed
  from the pool. Instead of closing an iterator, it will be returned to the
  pool. Iterators returned to the pool will never expire because snapshots
  itself are shorted lived and iterators are closed when the snapshot is closed.

  The pool is implemented by using a atom that holds a state for each column
  family. The state is a map with three properties: :output, :borrowed and
  :returned, were :output holds an iterator that will be returned next,
  :borrowed is a list holding all borrowed iterators except that of :output and
  :returned holds all returned iterators. The separate :output property is used
  to be able to detect whether an iterator could be borrowed from the previous
  state or not.

  The initial state is the empty map.

  The state transition for borrowing an iterator is implemented by the function
  `borrow-iterator`. It takes the first returned iterator, removes it from the
  list of returned iterators and puts it into :output. In case no returned
  iterator is available, :output will be nil. Additionally as any state
  transition function will do, a possible iterator from output is put into the
  :borrowed list.

  The state transition for returning an iterator is implemented by the function
  `return-iterator`. It takes the iterator to return as argument and moves it
  from :borrowed into :returned. Because :output can also contain a borrowed
  iterator, it first tests whether :output holds the iterator to return and if
  so just leaves borrowed unchanged. Otherwise it removes the iterator from
  :borrowed and as any state transition function will do, puts a possible
  iterator from output into the :borrowed list.

  The last state transition function is called `add-iterator` and is used when a
  new iterator from the original snapshot has to be created and put into the
  pool. It just puts the new iterator into :output and as any state transition
  function will do, puts a possible iterator from output into the :borrowed
  list.

  Borrowing an iterator including state transition and possibly creating a new
  iterator from the original snapshot is implemented in the `-new-iterator`
  protocol method of PoolingSnapshot. It first carries out a state transition of
  borrowing an iterator and looks at :output. If :output contains an iterator,
  it will just return it. If not, it will create a new iterator, carries out the
  state transition to add it into the state and returns it.

  Returning an iterator is implemented in the `close` method of PooledIterator."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.db.kv.protocols :as p])
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(def ^:private iterator-closed-anom
  (ba/fault "The iterator is closed."))

(defn- non-lazy-remove [pred list]
  (into '() (remove pred) list))

(defn- return-iterator [{:keys [output borrowed returned]} iterator]
  {:borrowed
   (if (identical? output iterator)
     borrowed
     (cond-> (non-lazy-remove #{iterator} borrowed) output (conj output)))
   :returned (conj returned iterator)})

(defn- return-iterator-cf
  "This function exists for performance reasons so that swap! doesn't need to be
  called with trailing args."
  [state column-family iterator]
  (update state column-family return-iterator iterator))

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
    (swap! pool return-iterator-cf column-family iter)))

(defn- borrow-iterator
  [{:keys [output borrowed] [first-returned & rest-returned] :returned}]
  {:output first-returned
   :borrowed (cond-> borrowed output (conj output))
   :returned rest-returned})

(defn- add-iterator [{:keys [output borrowed returned]} iterator]
  {:output iterator
   :borrowed (cond-> borrowed output (conj output))
   :returned returned})

(defn- add-iterator-cf
  "This function exists for performance reasons so that swap! doesn't need to be
  called with trailing args."
  [state column-family iterator]
  (update state column-family add-iterator iterator))

(defn- close! [x]
  (.close ^AutoCloseable x))

(defn- close-cf-pool! [{:keys [output borrowed returned]}]
  (some-> output close!)
  (run! close! borrowed)
  (run! close! returned))

(deftype PoolingSnapshot [snapshot pool]
  p/KvSnapshot
  (-new-iterator [_ column-family]
    (-> (or (-> (swap! pool update column-family borrow-iterator)
                column-family :output)
            (let [iter (p/-new-iterator snapshot column-family)]
              (swap! pool add-iterator-cf column-family iter)
              iter))
        (->PooledIterator pool column-family false)))

  (-snapshot-get [_ column-family key]
    (p/-snapshot-get snapshot column-family key))

  AutoCloseable
  (close [_]
    (run! close-cf-pool! (vals @pool))
    (close! snapshot)))

(defn pooling-snapshot ^AutoCloseable [snapshot]
  (->PoolingSnapshot snapshot (atom {})))
