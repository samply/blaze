(ns blaze.db.kv.protocols)

(defprotocol KvIterator
  "A mutable iterator over a KvSnapshot."

  (-valid [iter])

  (-seek-to-first [iter])

  (-seek-to-last [iter])

  (-seek [iter target])

  (-seek-buffer [iter target])

  (-seek-for-prev [iter target])

  (-seek-for-prev-buffer [iter target])

  (-next [iter])

  (-prev [iter])

  (-key [iter] [iter buf])

  (-value [iter] [iter buf]))

(defprotocol KvSnapshot
  "A snapshot of the contents of a KvStore."

  (-new-iterator [snapshot column-family])

  (-snapshot-get [snapshot column-family key]))

(defprotocol KvStore
  "A key-value store."

  (-new-snapshot [store])

  (-get [store column-family key])

  (-put [store entries])

  (-delete [store entries])

  (-write [store entries])

  (-estimate-num-keys [store column-family])

  (-estimate-scan-size [store column-family key-range])

  (-compact [store column-family]))
