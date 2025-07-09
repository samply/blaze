(ns blaze.db.impl.index.t-by-instant
  "Functions for accessing the TByInstant index.

  The TByInstant index is used to determine the `t` of a real point in time.
  This functionality is needed to support the `since` parameter in history
  queries."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.iterators :as i])
  (:import
   [com.google.common.primitives Longs]))

(set! *warn-on-reflection* true)

(defn- encode-key [instant]
  (Longs/toByteArray (inst-ms instant)))

(defn t-by-instant
  "Returns the logical timestamp `t` of the database that was created at or
  before `instant` orÔ
  nil if there is none."
  [snapshot instant]
  (i/seek-value snapshot :t-by-instant-index bb/get-long! 0
                (bs/from-byte-array (encode-key instant))))

(defn index-entry
  "Returns an entry of the TByInstant index build from `instant` and `t`."
  [instant t]
  [:t-by-instant-index (encode-key instant) (Longs/toByteArray t)])
