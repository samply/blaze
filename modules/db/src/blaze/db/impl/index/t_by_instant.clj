(ns blaze.db.impl.index.t-by-instant
  "Functions for accessing the TByInstant index."
  (:require
    [blaze.db.kv :as kv])
  (:import
    [com.google.common.primitives Longs]))


(set! *warn-on-reflection* true)


(defn- encode-key [instant]
  (Longs/toByteArray (inst-ms instant)))


(defn- t-by-instant* [iter instant]
  (kv/seek! iter (encode-key instant))
  (when (kv/valid? iter)
    (Longs/fromByteArray (kv/value iter))))


(defn t-by-instant
  "Returns the `t` of the database that was created at or before `instant` or
  nil if there is none."
  [snapshot instant]
  (with-open [iter (kv/new-iterator snapshot :t-by-instant-index)]
    (t-by-instant* iter instant)))


(defn index-entry
  "Returns an entry of the TByInstant index build from `instant` and `t`."
  [instant t]
  [:t-by-instant-index (encode-key instant) (Longs/toByteArray t)])
