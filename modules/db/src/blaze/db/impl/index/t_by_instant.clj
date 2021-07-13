(ns blaze.db.impl.index.t-by-instant
  "Functions for accessing the TByInstant index."
  (:require
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.kv :as kv])
  (:import
    [com.google.common.primitives Longs]))


(set! *warn-on-reflection* true)


(defn- t-by-instant* [iter instant]
  (let [buf (bb/allocate-direct Long/BYTES)]
    (bb/put-long! buf (inst-ms instant))
    (bb/flip! buf)
    (kv/seek-buffer! iter buf)
    (when (kv/valid? iter)
      (bb/clear! buf)
      (kv/value! iter buf)
      (bb/get-long! buf))))


(defn t-by-instant
  "Returns the `t` of the database that was created at or before `instant` or
  nil if there is none."
  [snapshot instant]
  (with-open [iter (kv/new-iterator snapshot :t-by-instant-index)]
    (t-by-instant* iter instant)))


(defn- encode-key [instant]
  (Longs/toByteArray (inst-ms instant)))


(defn- encode-value [^long t]
  (Longs/toByteArray t))


(defn index-entry
  "Returns an entry of the TByInstant index build from `instant` and `t`."
  [instant t]
  [:t-by-instant-index (encode-key instant) (encode-value t)])
