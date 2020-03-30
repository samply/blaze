(ns blaze.db.kv
  "Protocols for key-value store backend implementations."
  (:refer-clojure :exclude [get key next]))


(defprotocol KvIterator
  "A mutable iterator over a KvSnapshot."

  (-seek [iter target])

  (-seek-for-prev [iter target])

  (seek-to-first [iter])

  (seek-to-last [iter])

  (next [iter]
    "Moves this iterator to the next entry and returns the key of the entry if
    there is one.

    Must not be called if a previous operation returned nil.")

  (prev [iter]
    "Moves this iterator to the previous entry and returns the key of the entry
    if there is one.

    Must not be called if a previous operation returned nil.")

  (valid [iter]
    "")

  (key [iter]
    "")

  (value [iter]
    "Returns the value of the current entry of this iterator.

    Must not be called if a previous operation returned nil."))


(defn seek
  "Positions this iterator at the first entry whose key is at or past `target`
  and returns the key of the entry if there is one.

  The `target` is a byte array describing a key or a key prefix to seek for.

  Must not be called if a previous operation returned nil."
  [iter target]
  (-seek iter target))


(defn seek-for-prev
  "Positions this iterator at the first entry whose key is at or before `target`
  and returns the key of the entry if there is one.

  The `target` is a byte array describing a key or a key prefix to seek for.

  Must not be called if a previous operation returned nil."
  [iter target]
  (-seek-for-prev iter target))


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

  (get [store key] [store column-family key]
    "Returns the value if there is any.")

  (-put [store entries] [store key value])

  (delete [store keys]
    "Deletes keys.")

  (write [store entries]
    "Entries are either triples of operator, key and value or quadruples of
    operator, column-family, key and value.

    Operators are :put, :merge and :delete.

    Writes are atomic. Blocks."))


(defn put
  "Entries are either tuples of key and value or triples of column-family, key
  and value. Puts are atomic. Blocks."
  ([store entries]
   (-put store entries))
  ([store key value]
   (-put store key value)))
