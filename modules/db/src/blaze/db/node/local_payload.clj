(ns blaze.db.node.local-payload
  "Holds the resources of in-flight transactions sent as local payload through
  the transaction log softly reachable so that the garbage collector can
  discard them under memory pressure."
  (:import
   [java.lang.ref SoftReference]))

(set! *warn-on-reflection* true)

(defn wrap
  "Wraps `entries` into a payload from which they can be discarded under
  memory pressure."
  [entries]
  (SoftReference. entries))

(defn unwrap
  "Returns the entries wrapped in `payload` or nil if they were discarded
  under memory pressure."
  [payload]
  (.get ^SoftReference payload))
