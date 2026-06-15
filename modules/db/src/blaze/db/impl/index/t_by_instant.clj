(ns blaze.db.impl.index.t-by-instant
  "Functions for accessing the TByInstant index.

  The TByInstant index is used to determine the `t` of a real point in time.
  This functionality is needed to support the `since` parameter in history
  queries.

  The key of the index is the instant encoded as its big-endian milliseconds
  since epoch. Because keys are compared as unsigned bytes, this encoding only
  preserves the natural order for non-negative values. Instants before the epoch
  (1970-01-01T00:00:00Z) have their sign bit set and would sort as greater than
  later instants, breaking the index. Such instants are therefore unsupported,
  and callers have to ensure the instant is at or after the epoch."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.iterators :as i])
  (:import
   [com.google.common.primitives Longs]))

(set! *warn-on-reflection* true)

(defn- encode-key [instant]
  (let [ms (inst-ms instant)]
    (assert (not (neg? ms))
            "The TByInstant index doesn't support instants before the epoch.")
    (Longs/toByteArray ms)))

(defn t-by-instant
  "Returns the logical timestamp `t` of the database that was created at or
  before `instant` or nil if there is none."
  [snapshot instant]
  (i/seek-value snapshot :t-by-instant-index bb/get-long! 0
                (bs/from-byte-array (encode-key instant))))

(defn index-entry
  "Returns an entry of the TByInstant index build from `instant` and `t`."
  [instant t]
  [:t-by-instant-index (encode-key instant) (Longs/toByteArray t)])
