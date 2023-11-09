(ns blaze.db.impl.index.resource-as-of
  "Functions for accessing the ResourceAsOf index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i]
    [blaze.db.impl.macros :refer [with-open-coll]]
    [blaze.db.kv :as kv])
  (:import
    [clojure.lang IFn IReduceInit Sequential]
    [com.google.common.primitives Ints]
    [java.lang AutoCloseable]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long except-id-key-size
  (+ codec/tid-size codec/t-size))


(defn- focus-id!
  "Reduces the limit of `kb` in order to hide the t and focus on id solely."
  [kb]
  (bb/set-limit! kb (- (bb/limit kb) codec/t-size)))


(defn- copy-id!
  "Copies the id bytes from the key buffer into the id buffer."
  [kb ib]
  (let [id-size (bb/remaining kb)]
    (bb/set-limit! ib id-size)
    (bb/put-byte-buffer! ib kb)
    (bb/flip! ib)
    (bb/rewind! ib))
  (bb/set-limit! kb (unchecked-add-int (bb/limit kb) codec/t-size)))


(defn- skip-id!
  "Does the same to `position` and `limit` of `kb` as `copy-id!` but doesn't
  copy anything."
  [kb ib]
  (bb/set-position! kb (unchecked-add-int (bb/position kb) (bb/remaining ib)))
  (bb/set-limit! kb (unchecked-add-int (bb/limit kb) codec/t-size)))


(defn- id-marker
  "Compares the id part of the current key buffer with the id buffer that may
  contain previously seen ids.

  Returns true if the id changed over the previously seen one. Keeps the id
  buffer up to date."
  [ib]
  (fn [kb]
    (focus-id! kb)
    (cond
      (zero? (bb/limit ib))
      (do
        (copy-id! kb ib)
        false)

      (= kb ib)
      (do
        (skip-id! kb ib)
        false)

      :else
      (do
        (copy-id! kb ib)
        true))))


(defn- tid-marker [tid-box ib id-marker]
  (fn [kb]
    (let [tid (bb/get-int! kb)
          last-tid @tid-box]
      (cond
        (nil? last-tid)
        (do
          (vreset! tid-box tid)
          (id-marker kb))

        (= tid last-tid)
        (id-marker kb)

        :else
        (do
          (vreset! tid-box tid)
          (bb/set-limit! ib 0)
          (id-marker kb)
          true)))))


(defn- new-entry!
  "Creates a new resource handle entry."
  [tid ib vb t]
  (rh/resource-handle tid (codec/id (bb/array ib) 0 (bb/remaining ib)) t vb))


(defn- type-entry-creator
  "Returns a function of no argument which creates a new resource handle entry
  if the `t` in `kb` at the time of invocation is less than or equal `base-t`.

  Uses `raoi` to read the `hash` and `state` when needed. Supplies `tid` to the
  created entry."
  [tid raoi ib base-t]
  (fn [kb]
    (let [t (codec/descending-long (bb/get-long! kb))]
      (when (<= t ^long base-t)
        (new-entry! tid ib (bb/wrap (kv/value raoi)) t)))))


(defn- system-entry-creator
  [tid-box iter ib base-t]
  (fn [kb]
    (let [t (codec/descending-long (bb/get-long! kb))]
      (when (<= t ^long base-t)
        (new-entry! @tid-box ib (bb/wrap (kv/value iter)) t)))))


(defn- group-by-id
  "Returns a stateful transducer which takes flags from `id-marker` and supplies
  resource handle entries to the reduce function after id changes happen."
  [id-marker entry-creator]
  (let [state (volatile! nil)
        search-entry! (fn [kb]
                        (when-let [e (entry-creator kb)]
                          (vreset! state e)))]
    (fn [rf]
      (fn
        ([] (rf))
        ([result]
         (let [result (if-let [e @state]
                        (do
                          (vreset! state nil)
                          (unreduced (rf result e)))
                        result)]
           (rf result)))
        ([result kb]
         (if (id-marker kb)
           (if-let [e @state]
             (let [result (rf result e)]
               (vreset! state nil)
               (when-not (reduced? result)
                 (search-entry! kb))
               result)
             (do
               (search-entry! kb)
               result))
           (if @state
             result
             (do
               (search-entry! kb)
               result))))))))


(defn- encode-key-buf [tid id t]
  (-> (bb/allocate (unchecked-add-int except-id-key-size (bs/size id)))
      (bb/put-int! tid)
      (bb/put-byte-string! id)
      (bb/put-long! (codec/descending-long t))))


(defn encode-key
  "Encodes the key of the ResourceAsOf index from `tid`, `id` and `t`."
  [tid id t]
  (bb/array (encode-key-buf tid id t)))


(defn- starts-with-tid? [^long tid]
  (fn [kb] (= tid (bb/get-int! kb))))


(def ^:private remove-deleted-xf
  (remove rh/deleted?))


(defn- type-list-xf [raoi t tid]
  (let [ib (bb/set-limit! (bb/allocate codec/max-id-size) 0)
        entry-creator (type-entry-creator tid raoi ib t)]
    (comp
      i/key-reader
      (take-while (starts-with-tid? tid))
      (group-by-id (id-marker ib) entry-creator)
      remove-deleted-xf)))


(defn- start-key
  ([tid]
   (-> (Ints/toByteArray tid) bs/from-byte-array))
  ([tid start-id t]
   (-> (encode-key-buf tid start-id t)
       bb/flip!
       bs/from-byte-buffer!)))


(defn type-list
  "Returns a reducible collection of all resource handles of type with `tid`
  ordered by resource id.

  The list starts at the optional `start-id`.

  The ResourceAsOf index consists of keys with three parts: `tid`, `id` and
  `t`. The `tid` is a 4-byte hash of the resource type, the `id` a variable
  length byte array of the resource id and `t` is an 8-byte long of the
  transaction number. The value of the ResourceAsOf index contains two parts:
  `hash` and `state`. The `hash` is a 32-byte content hash of the resource and
  the `state` is an 8-byte long encoding create, put, delete state and a local
  version counter of the resource.

  The ResourceAsOf index contains one entry for each resource version. For the
  type list, only that versions not newer than `t` are returned. For example, an
  index containing two versions of the same resource looks like this:

    < tid-0, id-0, t=2 > < hash-2, state-2 >
    < tid-0, id-0, t=1 > < hash-1, state-1 >

  Here the `tid` and `id` are the same, because the resource is the same and
  only the versions differ. The newer version has a `t` of `2` and the older
  version a `t` of `1`. `t` values are simply an incrementing transaction
  number. The content hashes and states also differ. A `type-list` call with a
  `t` of two should return the newest version of the resource, were a call with
  a `t` of `1` should return the older version.

  The implementation iterates over the ResourceAsOf index, starting with `tid`
  and possible `start-id`. It goes from higher `t` values to lower `t` values
  because they are encoded in descending order.

  For each entry in the index, the following things are done:

   * check if the end of the index is reached
   * check if the first 4 bytes are still the same `tid` as given
   * for each `id` bytes seen over multiple entries, return that entry with
     `t` less than or equal to the `t` given

  A non-optimized implementation would use the following transducer, assuming
  the start with a collection of index entries starting at the right entry and
  ending at the end of the index:

    (comp
      (map decode-entry)
      (take-while tid-valid?)
      (partition-by id)
      (map pick-entry-according-to-t))

  First, the whole entry consisting of its key and value are decoded completely
  into an immutable data structure. Than the `tid` is tested against the given
  `tid` in order to stop iterating before hitting the next type. Third, all
  entries are partitioned by id which each partition containing a list of
  entries with the same id. Last, the entry with the right `t` is picked.

  This non-optimized implementation has several disadvantages. First, each entry
  is decoded fully but the `t`, `hash` and `state` part is only needed from
  fewer entries. Even the `id` has only to be compared to a reference `id`, but
  not fully decoded, for non-matching entries. Second `partition-by` creates an
  vector of all entries with the same id. This leads to more object allocation
  and time spend.

  The implementation used here avoids excessive object allocation altogether.

  Uses three byte buffers to avoid object allocation as much as
  possible. The first buffer is an off-heap key buffer. Each key is read into
  the key buffer. The second buffer is an heap allocated id buffer. The id bytes
  of the first key are copied into the id buffer and later copied only on id
  change. To detect id changes, the id part of the key buffer and the id buffer
  are compared. When a resource is created the heap allocated byte array of the
  id buffer is fed into the string constructor. The string constructor will copy
  the bytes from the id buffer for immutability. So for each returned resource
  handle, a String and the internal byte array, it used, are allocated. The
  second byte array which is allocated for each resource handle is the byte
  array of the hash. No further byte arrays are allocated during the whole
  iteration. The state and t which are both longs are read from the off-heap key
  and value buffer. The hash and state which are read from the value buffer are
  only read once for each resource handle."
  ([{:keys [snapshot t]} tid]
   (reify
     Sequential
     IReduceInit
     (reduce [_ f init]
       (with-open [raoi (kv/new-iterator snapshot :resource-as-of-index)]
         (transduce
           (type-list-xf raoi t tid)
           (completing f)
           init
           (i/iter! raoi (start-key tid)))))))
  ([{:keys [snapshot t]} tid start-id]
   (reify
     Sequential
     IReduceInit
     (reduce [_ f init]
       (with-open [raoi (kv/new-iterator snapshot :resource-as-of-index)]
         (transduce
           (type-list-xf raoi t tid)
           (completing f)
           init
           (i/iter! raoi (start-key tid start-id t))))))))


(defn- system-list-xf [raoi t start-tid]
  (let [tid-box (volatile! start-tid)
        ib (bb/set-limit! (bb/allocate codec/max-id-size) 0)]
    (comp
      i/key-reader
      (group-by-id (tid-marker tid-box ib (id-marker ib))
                   (system-entry-creator tid-box raoi ib t))
      remove-deleted-xf)))


(defn system-list
  "Returns a reducible collection of all resource handles ordered by resource
  tid and resource id.

  The list starts at the optional `start-tid` and `start-id`."
  ([{:keys [snapshot t]}]
   (reify
     Sequential
     IReduceInit
     (reduce [_ f init]
       (with-open [raoi (kv/new-iterator snapshot :resource-as-of-index)]
         (transduce
           (system-list-xf raoi t nil)
           (completing f)
           init
           (i/iter! raoi))))))
  ([{:keys [snapshot t]} start-tid start-id]
   (reify
     Sequential
     IReduceInit
     (reduce [_ f init]
       (with-open [raoi (kv/new-iterator snapshot :resource-as-of-index)]
         (transduce
           (system-list-xf raoi t start-tid)
           (completing f)
           init
           (i/iter! raoi (start-key start-tid start-id t))))))))


(defn decoder
  "Returns a function which decodes an resource handle out of a key and a value
  byte buffers from the ResourceAsOf index.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  The decode function creates only five objects, the resource handle, the String
  for the id, the byte array inside the String, the ByteString and the byte
  array inside the ByteString for the hash.

  Both byte buffers are changed during decoding and have to be reset accordingly
  after decoding."
  []
  (let [ib (byte-array codec/max-id-size)]
    (fn [kb vb]
      (rh/resource-handle
        (bb/get-int! kb)
        (let [id-size (- (bb/remaining kb) codec/t-size)]
          (bb/copy-into-byte-array! kb ib 0 id-size)
          (codec/id ib 0 id-size))
        (codec/descending-long (bb/get-long! kb))
        vb))))


(defn- instance-history-key-valid? [^long tid id ^long end-t]
  (fn [resource-handle]
    (and (= (rh/tid resource-handle) tid)
         (= (rh/id resource-handle) id)
         (< end-t (rh/t resource-handle)))))


(defn instance-history
  "Returns a reducible collection of all versions between `start-t` (inclusive)
  and `end-t` (exclusive) of the resource with `tid` and `id`.

  Versions are resource handles."
  [snapshot tid id start-t end-t]
  (coll/eduction
    (take-while (instance-history-key-valid? tid (codec/id-string id) end-t))
    (with-open-coll [raoi (kv/new-iterator snapshot :resource-as-of-index)]
      (i/kvs! raoi (decoder) (start-key tid id start-t)))))


(defn- resource-handle** [raoi tid id t]
  (let [tb (encode-key-buf tid id t)]
    (kv/seek! raoi (bb/array tb))
    (when (kv/valid? raoi)
      ;; read key
      (let [kb (bb/wrap (kv/key raoi))]
        ;; we have to check that we are still on target, because otherwise we
        ;; would find the next resource
        ;; focus target buffer on tid and id
        (bb/rewind! tb)
        (bb/set-limit! tb (unchecked-subtract-int (bb/limit tb) codec/t-size))
        ;; focus key buffer on tid and id
        (bb/set-limit! kb (unchecked-subtract-int (bb/limit kb) codec/t-size))
        (when (= tb kb)
          ;; focus key buffer on t
          (let [limit (bb/limit kb)]
            (bb/set-position! kb limit)
            (bb/set-limit! kb (unchecked-add-int limit codec/t-size)))
          ;; create resource handle
          (rh/resource-handle
            tid
            (codec/id-string id)
            (codec/descending-long (bb/get-long! kb))
            (bb/wrap (kv/value raoi))))))))


(defn resource-handle
  "Returns a function which can be called with a `tid`, an `id` and an optional
  `t` which will lookup the resource handle in `snapshot`.

  The `t` is the default if `t` isn't given at the returned function.

  The returned function can't be called concurrently and has to be closed in
  order to close the ResourceAsOf iterator."
  ^AutoCloseable
  [snapshot t]
  (let [raoi (kv/new-iterator snapshot :resource-as-of-index)]
    (reify
      IFn
      (invoke [this tid id]
        (.invoke this tid id t))
      (invoke [_ tid id t]
        (resource-handle** raoi tid id t))
      AutoCloseable
      (close [_]
        (.close ^AutoCloseable raoi)))))


(defn num-of-instance-changes
  "Returns the number of changes between `start-t` (inclusive) and `end-t`
  (inclusive) of the resource with `tid` and `id`."
  [resource-handle tid id start-t end-t]
  (- (long (:num-changes (resource-handle tid id start-t) 0))
     (long (:num-changes (resource-handle tid id end-t) 0))))
