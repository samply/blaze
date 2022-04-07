(ns blaze.db.kv
  "Protocols for key-value store backend implementations.

  All functions block the current thread while doing I/O."
  (:refer-clojure :exclude [get key])
  (:import
    [java.lang AutoCloseable]))


(set! *warn-on-reflection* true)


;; A mutable iterator over a KvSnapshot.
#_{:clj-kondo/ignore [:unused-binding]}
(definterface KvIterator
  (^boolean valid [])

  (^void seekToFirst [])

  (^void seekToLast [])

  (^void seek [^bytes target])

  (^void seekBuffer [^java.nio.ByteBuffer target])

  (^void seekForPrev [^bytes target])

  (^void next [])

  (^void prev [])

  (key [])

  (^int key [^java.nio.ByteBuffer buf])

  (value [])

  (^int value [^java.nio.ByteBuffer buf]))


(defn valid?
  "Returns true if `iter` is positioned at an entry."
  {:inline (fn [iter] `(.valid ~(with-meta iter {:tag `KvIterator})))}
  [iter]
  (.valid ^KvIterator iter))


(defn seek-to-first!
  "Positions `iter` at the first entry of its source.

  The iterator will be valid if its source is not empty."
  {:inline
   (fn [iter] `(.seekToFirst ~(with-meta iter {:tag `KvIterator})))}
  [iter]
  (.seekToFirst ^KvIterator iter))


(defn seek-to-last!
  "Positions `iter` at the last entry of its source.

  The iterator will be valid if its source is not empty."
  {:inline
   (fn [iter] `(.seekToLast ~(with-meta iter {:tag `KvIterator})))}
  [iter]
  (.seekToLast ^KvIterator iter))


(defn seek!
  "Positions `iter` at the first entry of its source whose key is at or past
  `target`.

  The `target` is a byte array describing a key or a key prefix to seek for.

  The iterator will be valid if its source contains a key at or past `target`."
  {:inline
   (fn [iter target]
     `(.seek ~(with-meta iter {:tag `KvIterator}) ~target))}
  [iter target]
  (.seek ^KvIterator iter target))


(defn seek-buffer!
  "Positions `iter` at the first entry of its source whose key is at or past
  `target`.

  The `target` is a byte buffer describing a key or a key prefix to seek for.

  The iterator will be valid if its source contains a key at or past `target`."
  {:inline
   (fn [iter target]
     `(.seekBuffer ~(with-meta iter {:tag `KvIterator}) ~target))}
  [iter target]
  (.seekBuffer ^KvIterator iter target))


(defn seek-for-prev!
  "Positions `iter` at the first entry of its source whose key is at or before
  `target`.

  The `target` is a byte array describing a key or a key prefix to seek for.

  The iterator will be valid if its source contains a key at or before `target`."
  {:inline
   (fn [iter target]
     `(.seekForPrev ~(with-meta iter {:tag `KvIterator}) ~target))}
  [iter target]
  (.seekForPrev ^KvIterator iter target))


(defn next!
  "Moves `iter` to the next entry of its source.

  Requires `iter` to be valid."
  {:inline (fn [iter] `(.next ~(with-meta iter {:tag `KvIterator})))}
  [iter]
  (.next ^KvIterator iter))


(defn prev!
  "Moves this iterator to the previous entry.

  Requires `iter` to be valid."
  {:inline (fn [iter] `(.prev ~(with-meta iter {:tag `KvIterator})))}
  [iter]
  (.prev ^KvIterator iter))


(defn key
  "Returns the key of the current entry of `iter`.

  Requires `iter` to be valid."
  [iter]
  (.key ^KvIterator iter))


(defn key!
  "Puts the key of current entry of `iter` in `buf`.

  Uses the position of `buf` and sets the limit of `buf` according to the key
  size. Supports direct buffers only.

  Returns the size of the actual key. If the key is greater than the length of
  `buf`, then it indicates that the size of the `buf` is insufficient and a
  partial result is put."
  {:inline (fn [iter buf] `(.key ~(with-meta iter {:tag `KvIterator}) ~buf))}
  [iter buf]
  (.key ^KvIterator iter buf))


(defn value
  "Returns the value of the current entry of `iter`.

  Requires `iter` to be valid."
  [iter]
  (.value ^KvIterator iter))


(defn value!
  "Puts the value of current entry of `iter` in `buf`.

  Uses the position of `buf` and sets the limit of `buf` according to the value
  size. Supports direct buffers only.

  Returns the size of the actual value. If the value is greater than the length
  of `buf`, then it indicates that the size of the `buf` is insufficient and a
  partial result is put."
  {:inline (fn [iter buf] `(.value ~(with-meta iter {:tag `KvIterator}) ~buf))}
  [iter buf]
  (.value ^KvIterator iter buf))


(defprotocol KvSnapshot
  "A snapshot of the contents of a KvStore."

  (-new-iterator [snapshot] [snapshot column-family])

  (-snapshot-get [snapshot key] [snapshot column-family key]))


(defn new-iterator
  "Return an iterator over the contents of the database.

  The result is initially invalid, so the caller must call one of the seek
  functions with the iterator before using it.

  Throws an anomaly if `column-family` was not found.

  Iterators have to be closed after usage."
  (^AutoCloseable
   [snapshot]
   (-new-iterator snapshot))
  (^AutoCloseable
   [snapshot column-family]
   (-new-iterator snapshot column-family)))


(defn snapshot-get
  "Returns a new byte array storing the value associated with the `key` if any."
  ([snapshot key]
   (-snapshot-get snapshot key))
  ([snapshot column-family key]
   (-snapshot-get snapshot column-family key)))


(defprotocol KvStore
  "A key-value store."

  (-new-snapshot [store])

  (-get [store key] [store column-family key])

  (-multi-get [store keys])

  (-put [store entries] [store key value])

  (-delete [store keys])

  (-write [store entries]))


(defn store? [x]
  (satisfies? KvStore x))


(defn new-snapshot
  "Opens a new snapshot of `store`.

  Snapshots have to be closed after usage."
  ^AutoCloseable
  [store]
  (-new-snapshot store))


(defn get
  "Returns the value of `key` in `column-family` (optional) or nil if not found.

  Blocks the current thread."
  ([store key]
   (-get store key))
  ([store column-family key]
   (-get store column-family key)))


(defn multi-get
  "Returns a map of key to value of all found entries."
  ([store keys]
   (-multi-get store keys)))


(defn put!
  "Stores either `entries` or the pair of `key` and `value`.

  Entries are either tuples of key and value or triples of column-family, key
  and value.

  Throws an anomaly if a column-family of an entry was not found.

  Puts are atomic. Blocks. Returns nil."
  ([store entries]
   (-put store entries))
  ([store key value]
   (-put store key value)))


(defn delete!
  "Deletes entries with `keys`."
  [store keys]
  (-delete store keys))


(defn write!
  "Entries are either triples of operator, key and value or quadruples of
  operator, column-family, key and value.

  Operators are :put, :merge and :delete.

  Writes are atomic. Blocks."
  [store entries]
  (-write store entries))
