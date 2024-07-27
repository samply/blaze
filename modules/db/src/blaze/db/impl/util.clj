(ns blaze.db.impl.util
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn closer
  "Returns a transducer that closes `closeable` at the end of the transduction."
  [closable]
  (fn [rf]
    (fn
      ([result]
       (.close ^AutoCloseable closable)
       (rf result))
      ([result input]
       (rf result input)))))
