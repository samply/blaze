(ns blaze.db.kv
  "Protocols for key-value store backend implementations.

  All functions block the current thread while doing I/O."
  (:refer-clojure :exclude [get key])
  (:require
   [blaze.db.kv.iter-pool :as ip]
   [blaze.db.kv.protocols :as p])
  (:import
   [java.lang AutoCloseable]))

(defn valid?
  "Returns true if `iter` is positioned at an entry."
  [iter]
  (p/-valid iter))

(defn seek-to-first!
  "Positions `iter` at the first entry of its source.

  The iterator will be valid if its source is not empty."
  [iter]
  (p/-seek-to-first iter))

(defn seek-to-last!
  "Positions `iter` at the last entry of its source.

  The iterator will be valid if its source is not empty."
  [iter]
  (p/-seek-to-last iter))

(defn seek!
  "Positions `iter` at the first entry of its source whose key is at or past
  `target`.

  The `target` is a byte array describing a key or a key prefix to seek for.

  The iterator will be valid if its source contains a key at or past `target`."
  [iter target]
  (p/-seek iter target))

(defn seek-buffer!
  "Positions `iter` at the first entry of its source whose key is at or past
  `target`.

  The `target` is a byte buffer describing a key or a key prefix to seek for.

  The iterator will be valid if its source contains a key at or past `target`."
  [iter target]
  (p/-seek-buffer iter target))

(defn seek-for-prev!
  "Positions `iter` at the first entry of its source whose key is at or before
  `target`.

  The `target` is a byte array describing a key or a key prefix to seek for.

  The iterator will be valid if its source contains a key at or before `target`."
  [iter target]
  (p/-seek-for-prev iter target))

(defn seek-for-prev-buffer!
  "Positions `iter` at the first entry of its source whose key is at or before
  `target`.

  The `target` is a byte buffer describing a key or a key prefix to seek for.

  The iterator will be valid if its source contains a key at or before `target`."
  [iter target]
  (p/-seek-for-prev-buffer iter target))

(defn next!
  "Moves `iter` to the next entry of its source.

  Requires `iter` to be valid."
  [iter]
  (p/-next iter))

(defn prev!
  "Moves this iterator to the previous entry.

  Requires `iter` to be valid."
  [iter]
  (p/-prev iter))

(defn key
  "Returns the key of the current entry of `iter`.

  Requires `iter` to be valid."
  [iter]
  (p/-key iter))

(defn key!
  "Puts the key of current entry of `iter` in `buf`.

  Uses the position and limit of `buf` and sets the limit of `buf` according to
  the key size. Note: doesn't read bytes over the limit!

  Returns the size of the actual key. If the key is greater than the length of
  `buf`, then it indicates that the size of the `buf` is insufficient and a
  partial result was returned."
  [iter buf]
  (p/-key iter buf))

(defn value
  "Returns the value of the current entry of `iter`.

  Requires `iter` to be valid."
  [iter]
  (p/-value iter))

(defn value!
  "Puts the value of current entry of `iter` in `buf`.

  Uses the position of `buf` and sets the limit of `buf` according to the value
  size.

  Returns the size of the actual value. If the value is greater than the length
  of `buf`, then it indicates that the size of the `buf` is insufficient and a
  partial result was returned."
  [iter buf]
  (p/-value iter buf))

(defn new-iterator
  "Return an iterator over the contents of `column-family`.

  The result is initially invalid, so the caller must call one of the seek
  functions with the iterator before using it.

  Throws an anomaly if `column-family` was not found.

  Iterators have to be closed after usage."
  ^AutoCloseable
  [snapshot column-family]
  (p/-new-iterator snapshot column-family))

(defn snapshot-get
  "Returns a new byte array storing the value associated with the `key` in
  `column-family` if any."
  [snapshot column-family key]
  (p/-snapshot-get snapshot column-family key))

(defn new-snapshot
  "Opens a new snapshot of `store`.

  Snapshots have to be closed after usage."
  ^AutoCloseable
  [store]
  (ip/pooling-snapshot (p/-new-snapshot store)))

(defn get
  "Returns the value of `key` in `column-family` or nil if not found.

  Blocks the current thread."
  [store column-family key]
  (p/-get store column-family key))

(defn put!
  "Stores `entries` that are triples of column-family, key and value.

  Throws an anomaly if a column-family of an entry was not found.

  Puts are atomic. Blocks. Returns nil."
  [store entries]
  (p/-put store entries))

(defn delete!
  "Deletes `entries` that are tuples of column-family and key.

  Deletes are atomic. Blocks. Returns nil."
  [store entries]
  (p/-delete store entries))

(defn write!
  "Entries are quadruples of operator, column-family, key and value.

  Operators are :put, :merge and :delete.

  Delete entries have no value.

  Writes are atomic. Blocks."
  [store entries]
  (p/-write store entries))

(defn estimate-num-keys
  "Returns the estimated number of keys in `column-family` of `store`.

  Returns an anomaly if `column-family` was not found."
  [store column-family]
  (p/-estimate-num-keys store column-family))

(defn estimate-scan-size
  "Returns a relative estimation of the amount of work to do while scanning the
  `key-range` in `column-family`.

  The metric is relative and unitless. It can be only used to compare the amount
  of scan work between different column families and key ranges."
  [store column-family key-range]
  (p/-estimate-scan-size store column-family key-range))

(defn compact!
  "Compacts the storage of `column-family` of `store`.

  Returns a CompletableFuture that will complete after the compaction is done
  or will complete exceptionally with an anomaly if `column-family` was not
  found."
  [store column-family]
  (p/-compact store column-family))
