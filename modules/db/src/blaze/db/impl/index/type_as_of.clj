(ns blaze.db.impl.index.type-as-of
  "Functions for accessing the TypeAsOf index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.byte-string-builder :as bsb]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.util :as u :refer [read-t!]]
   [blaze.db.impl.iterators :as i]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long tid-t-size
  (+ codec/tid-size codec/t-size))

(defn- history-decoder
  "Returns a transducer decoding key and value buffers from the TypeAsOf index
  into resource handles. Skips entries purged at `base-t` and terminates the
  reduction once `since-t` is reached, because the index is ordered by descending
  `t` within a `tid` prefix.

  Can only be used from one thread.

  Both buffers are changed during decoding and have to be reset accordingly
  after decoding."
  [tid base-t since-t]
  (let [read-id! (u/id-reader)]
    (fn [rf]
      (fn
        ([result] (rf result))
        ([result [kb vb]]
         (bb/set-position! kb codec/tid-size)
         (let [resource-t (read-t! kb)]
           (if (< (long since-t) resource-t)
             (let [handle (rh/resource-handle! tid (read-id! kb) resource-t
                                               base-t vb)]
               (cond-> result handle (rf handle)))
             (reduced result))))))))

(defn encode-key
  "Encodes the key of the TypeAsOf index from `tid`, `t` and `id`."
  [tid t id]
  (-> (bsb/allocate (unchecked-add-int tid-t-size (bs/size id)))
      (bsb/put-int! tid)
      (bsb/put-long! (codec/descending-long ^long t))
      (bsb/put-byte-string! id)
      bsb/to-bytes))

(defn- start-key [tid start-t start-id]
  (if start-id
    (bs/from-byte-array (encode-key tid start-t start-id))
    (-> (bsb/allocate tid-t-size)
        (bsb/put-int! tid)
        (bsb/put-long! (codec/descending-long ^long start-t))
        bsb/build)))

(defn type-history
  "Returns a reducible collection of all historic resource handles with type
  `tid` of the database with the point in time `t` between `start-t` (inclusive)
  and `start-id` (optional, inclusive)."
  [snapshot t since-t tid start-t start-id]
  (i/prefix-entries
   snapshot :type-as-of-index
   (history-decoder tid t since-t)
   codec/tid-size (start-key tid start-t start-id)))

(defn- decode-key
  "Decodes the key buffer `kb` from the TypeAsOf index into a `[t id]` tuple. The
  value buffer is ignored, because currency is determined by probing the
  ResourceAsOf index instead of decoding the version here.

  Can only be used from one thread.

  The key buffer is changed during decoding and has to be reset accordingly
  after decoding."
  [kb]
  (bb/set-position! kb codec/tid-size)
  [(read-t! kb) (bs/from-byte-buffer! kb)])

(def ^:private asc-decoder
  "Transducer decoding key and value buffers from the TypeAsOf index into
  `[t id]` tuples.

  Can only be used from one thread.

  Both buffers are changed during decoding and have to be reset accordingly
  after decoding."
  (map (fn [[kb _vb]] (decode-key kb))))

(defn- desc-decoder
  "Like `asc-decoder` but terminates the reduction once `since-t` is reached.
  Only suitable for descending `t` iteration, where `since-t` is the lower bound.

  Can only be used from one thread.

  Both buffers are changed during decoding and have to be reset accordingly
  after decoding."
  [since-t]
  (fn [rf]
    (fn
      ([result] (rf result))
      ([result [kb _vb]]
       (let [[t :as t-id] (decode-key kb)]
         (if (< (long since-t) (long t))
           (rf result t-id)
           (reduced result)))))))

(defn- entry-id [[_t id]] id)

(defn- current-entry?
  "Returns true if `handle` is the current, non-deleted version of the resource
  and its `t` equals the `t` of the TypeAsOf `entry`."
  [[resource-t] handle]
  (and (not (rh/deleted? handle))
       (= (long resource-t) (:t handle))))

(defn- current-version-xf
  "Returns a transducer that turns `[t id]` tuples into the resource handles of
  the current, non-deleted versions, probing the ResourceAsOf index of
  `batch-db`."
  [batch-db tid]
  (rao/resource-handle-type-xf batch-db tid entry-id current-entry?))

(defn type-list-desc
  "Returns a reducible collection of the resource handles of all current,
  non-deleted resources with type `tid` of `batch-db`, in descending `t` (i.e.
  `_lastUpdated`) order, between `start-t` (inclusive) and `since-t` (exclusive),
  optionally starting at `start-id`.

  Unlike `type-history`, only the current version of each resource is returned.
  Currency is determined by probing the ResourceAsOf index, so no resource
  handle is materialized for non-current versions."
  [batch-db tid since-t start-t start-id]
  (i/prefix-entries
   (:snapshot batch-db) :type-as-of-index
   (comp (desc-decoder since-t)
         (current-version-xf batch-db tid))
   codec/tid-size (start-key tid start-t start-id)))

(defn- type-list-asc* [batch-db tid start-key]
  (i/prefix-entries-prev
   (:snapshot batch-db) :type-as-of-index
   (comp asc-decoder
         (current-version-xf batch-db tid))
   codec/tid-size start-key))

(defn type-list-asc
  "Like `type-list-desc` but in ascending `t` order. The two-argument arity
  starts just above the `since-t` of `batch-db`, i.e. at the oldest resource in
  view; the four-argument arity resumes at `start-t`/`start-id` (inclusive).

  Ascending iteration needs no explicit lower bound in the decoder: the start key
  is built so the scan begins right above `since-t` (or `start-t`). `start-t` must
  not be smaller than `since-t`, because otherwise the scan would include
  resources outside the database view; this is asserted."
  {:arglists '([batch-db tid] [batch-db tid start-t start-id])}
  ([batch-db tid]
   (type-list-asc* batch-db tid (start-key tid (:since-t batch-db) nil)))
  ([{:keys [since-t] :as batch-db} tid start-t start-id]
   (assert (<= (long since-t) (long start-t))
           (format "start-t %d must not be smaller than since-t %d"
                   start-t since-t))
   (type-list-asc* batch-db tid (start-key tid start-t start-id))))
