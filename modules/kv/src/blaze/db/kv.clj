(ns blaze.db.kv
  "Protocols for key-value store backend implementations."
  (:refer-clojure :exclude [get key]))


(defprotocol KvIterator
  "A mutable iterator over a KvSnapshot."

  (-valid [iter])

  (-seek-to-first [iter])

  (-seek-to-last [iter])

  (-seek [iter target])

  (-seek-for-prev [iter target])

  (-next [iter])

  (-prev [iter])

  (-key [iter] [iter buf])

  (-value [iter] [iter buf]))


(defn valid?
  "Returns true if `iter` is positioned at an entry."
  [iter]
  (-valid iter))


(defn seek-to-first!
  "Positions `iter` at the first entry of its source.

  The iterator will be valid if its source is not empty."
  [iter]
  (-seek-to-first iter))


(defn seek-to-last!
  "Positions `iter` at the last entry of its source.

  The iterator will be valid if its source is not empty."
  [iter]
  (-seek-to-last iter))


(defn seek!
  "Positions `iter` at the first entry of its source whose key is at or past
  `target`.

  The `target` is a byte array describing a key or a key prefix to seek for.

  The iterator will be valid if its source contains a key at or past `target`."
  [iter target]
  (-seek iter target))


(defn seek-for-prev!
  "Positions `iter` at the first entry of its source whose key is at or before
  `target`.

  The `target` is a byte array describing a key or a key prefix to seek for.

  The iterator will be valid if its source contains a key at or before `target`."
  [iter target]
  (-seek-for-prev iter target))


(defn next!
  "Moves `iter` to the next entry of its source.

  Requires `iter` to be valid."
  [iter]
  (-next iter))


(defn prev!
  "Moves this iterator to the previous entry.

  Requires `iter` to be valid."
  [iter]
  (-prev iter))


(defn key
  "Returns the key of the current entry of `iter`.

  Requires `iter` to be valid."
  ([iter]
   (-key iter))
  ([iter buf]
   (-key iter buf)))


(defn value
  "Returns the value of the current entry of `iter`.

  Requires `iter` to be valid."
  ([iter]
   (-value iter))
  ([iter buf]
   (-value iter buf)))


(defprotocol KvSnapshot
  "A snapshot of the contents of a KvStore."

  (new-iterator
    ^java.io.Closeable [snapshot]
    ^java.io.Closeable [snapshot column-family])

  (snapshot-get [snapshot key] [snapshot column-family key]
    "Returns the value if there is any."))


(defprotocol KvStore
  "A key-value store."

  (new-snapshot ^java.io.Closeable [store])

  (-get [store key] [store column-family key])

  (-multi-get [store keys])

  (-put [store entries] [store key value])

  (delete [store keys]
    "Deletes keys.")

  (write [store entries]
    "Entries are either triples of operator, key and value or quadruples of
    operator, column-family, key and value.

    Operators are :put, :merge and :delete.

    Writes are atomic. Blocks."))


(defn get
  "Returns the value if there is any."
  ([store key]
   (-get store key))
  ([store column-family key]
   (-get store column-family key)))


(defn multi-get
  "Returns a map of key to value of all found entries."
  ([store keys]
   (-multi-get store keys)))


(defn put
  "Entries are either tuples of key and value or triples of column-family, key
  and value.

  Puts are atomic. Blocks. Returns nil."
  ([store entries]
   (-put store entries))
  ([store key value]
   (-put store key value)))

