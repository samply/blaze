(ns blaze.db.impl.index.resource-as-of
  "Functions for accessing the ResourceAsOf index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv]
   [blaze.fhir.hash :as hash])
  (:import
   [com.google.common.primitives Ints]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long except-id-key-size
  (+ codec/tid-size codec/t-size))

(def ^:private ^:const ^long max-key-size
  (+ except-id-key-size codec/max-id-size))

(def ^:const ^long min-value-size
  (+ hash/size codec/state-size))

(def ^:const ^long max-value-size
  (+ hash/size codec/state-size codec/t-size))

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
  "Creates a new resource handle entry if not purged at `base-t`."
  [tid ib vb t base-t]
  (rh/resource-handle! tid (codec/id (bb/array ib) 0 (bb/remaining ib)) t
                       base-t vb))

(defn- type-entry-creator
  "Returns a function which creates a new resource handle entry if the `t` in
  `kb` at the time of invocation is less than or equal `base-t` and greater than
  `since-t`."
  [tid ib base-t since-t]
  (fn [kb vb]
    (let [resource-t (codec/descending-long (bb/get-long! kb))]
      (when (and (< (long since-t) resource-t) (<= resource-t (long base-t)))
        (new-entry! tid ib vb resource-t base-t)))))

(defn- system-entry-creator
  [tid-box ib base-t since-t]
  (fn [kb vb]
    (let [resource-t (codec/descending-long (bb/get-long! kb))]
      (when (and (< (long since-t) resource-t) (<= resource-t (long base-t)))
        (new-entry! @tid-box ib vb resource-t base-t)))))

(defn- group-by-id
  "Returns a stateful transducer which takes flags from `id-marker` and supplies
  resource handle entries to the reduce function after id changes happen."
  [id-marker entry-creator]
  (fn [rf]
    (let [state (volatile! nil)
          search-entry! (fn [kb vb]
                          (when-let [e (entry-creator kb vb)]
                            (vreset! state e)))]
      (fn
        ([result]
         (let [result (if-let [e @state]
                        (do
                          (vreset! state nil)
                          (unreduced (rf result e)))
                        result)]
           (rf result)))
        ([result [kb vb]]
         (if (id-marker kb)
           (if-let [e @state]
             (let [result (rf result e)]
               (vreset! state nil)
               (when-not (reduced? result)
                 (search-entry! kb vb))
               result)
             (do
               (search-entry! kb vb)
               result))
           (if @state
             result
             (do
               (search-entry! kb vb)
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
  (fn [[kb]] (= tid (bb/get-int! kb))))

(def ^:private remove-deleted-xf
  (remove rh/deleted?))

(defn- type-list-xf [tid base-t since-t]
  (let [ib (bb/set-limit! (bb/allocate codec/max-id-size) 0)
        entry-creator (type-entry-creator tid ib base-t since-t)]
    (comp
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
  {:arglists '([batch-db tid] [batch-db tid start-id])}
  ([{:keys [snapshot t since-t]} tid]
   (i/entries snapshot :resource-as-of-index (type-list-xf tid t since-t)
              (start-key tid)))
  ([{:keys [snapshot t since-t]} tid start-id]
   (i/entries snapshot :resource-as-of-index (type-list-xf tid t since-t)
              (start-key tid start-id t))))

(defn- system-list-xf [t since-t start-tid]
  (let [tid-box (volatile! start-tid)
        ib (bb/set-limit! (bb/allocate codec/max-id-size) 0)]
    (comp
     (group-by-id (tid-marker tid-box ib (id-marker ib))
                  (system-entry-creator tid-box ib t since-t))
     remove-deleted-xf)))

(defn system-list
  "Returns a reducible collection of all resource handles ordered by resource
  tid and resource id.

  The list starts at the optional `start-tid` and `start-id`."
  ([{:keys [snapshot t since-t]}]
   (i/entries snapshot :resource-as-of-index (system-list-xf t since-t nil)))
  ([{:keys [snapshot t since-t]} start-tid start-id]
   (i/entries snapshot :resource-as-of-index (system-list-xf t since-t start-tid)
              (start-key start-tid start-id t))))

(defn- decoder
  "Returns a function which decodes a resource handle out of a key and a value
  byte buffers from the ResourceAsOf index if not purged at `t`.

  Both byte buffers are changed during decoding and have to be reset accordingly
  after decoding."
  [tid id tid-id-size base-t since-t]
  (fn [[kb vb]]
    (let [resource-t (codec/descending-long (bb/get-long! kb tid-id-size))]
      (when (< (long since-t) resource-t)
        (rh/resource-handle! tid id resource-t base-t vb)))))

(defn instance-history
  "Returns a reducible collection of all historic resource handles of the
  resource with `tid` and `id` of the database with the point in time `t`
  starting at `start-t`."
  [snapshot t since-t tid id start-t]
  (let [tid-id-size (+ codec/tid-size (bs/size id))]
    (i/prefix-entries
     snapshot :resource-as-of-index
     (keep (decoder tid (codec/id-string id) tid-id-size t since-t))
     (inc tid-id-size) (start-key tid id start-t))))

(defn- resource-handle* [iter target-buf key-buf value-buf tid id base-t since-t]
  (let [tid-id-size (+ codec/tid-size (bs/size id))
        key-size (+ tid-id-size codec/t-size)]
    (bb/clear! target-buf)
    (bb/put-int! target-buf tid)
    (bb/put-byte-string! target-buf id)
    (bb/put-long! target-buf (codec/descending-long base-t))
    (bb/flip! target-buf)
    (kv/seek-buffer! iter target-buf)
    (when (kv/valid? iter)
      ;; it's important to clear key-buf before reading, because otherwise
      ;; the limit could be too small
      (bb/clear! key-buf)
      (kv/key! iter key-buf)
      ;; we have to check that we are still on target, because otherwise we
      ;; would find the next resource. First, the length of the found key has
      ;; to equal our key size
      (when (= key-size (bb/limit key-buf))
        ;; focus target buffer on tid and id
        (bb/rewind! target-buf)
        (bb/set-limit! target-buf tid-id-size)
        ;; focus key buffer on tid and id
        (bb/set-limit! key-buf tid-id-size)
        (when (= target-buf key-buf)
          ;; focus key buffer on base-t
          (bb/set-limit! key-buf key-size)
          ;; read value
          (bb/clear! value-buf)
          (kv/value! iter value-buf)
          ;; build resource handle
          (let [resource-t (codec/descending-long (bb/get-long! key-buf tid-id-size))]
            (when (< (long since-t) resource-t)
              (rh/resource-handle!
               tid
               (codec/id-string id)
               resource-t
               base-t
               value-buf))))))))

(defn resource-handle
  "Returns the resource handle with `tid` and `id` at `t` in `snapshot` when
  found."
  [snapshot t since-t tid id]
  (let [target-buf (bb/allocate max-key-size)
        key-buf (bb/allocate max-key-size)
        value-buf (bb/allocate max-value-size)]
    (with-open [iter (kv/new-iterator snapshot :resource-as-of-index)]
      (resource-handle* iter target-buf key-buf value-buf tid id t since-t))))

(defn resource-handle-xf
  "Returns a stateful transducer that receives `[tid id]` tuples and emits
  resource handles when found in `snapshot` at `t`.

  Can only be used by a single thread."
  {:arglists '([batch-db])}
  [{:keys [snapshot t since-t]}]
  (fn [rf]
    (let [target-buf (bb/allocate max-key-size)
          key-buf (bb/allocate max-key-size)
          value-buf (bb/allocate max-value-size)
          iter (kv/new-iterator snapshot :resource-as-of-index)]
      (fn
        ([result]
         (.close ^AutoCloseable iter)
         (rf result))
        ([result [tid id]]
         (if-let [handle (resource-handle* iter target-buf key-buf value-buf
                                           tid id t since-t)]
           (rf result handle)
           result))))))

(defn resource-handle-type-xf
  "Returns a stateful transducer that receives input and emits resource handles
  when found.

  The `id-extractor` is used to extract the resource id as byte-string from the
  input. The `matcher` is given the input and the resource handle to decide
  whether to emit the resource handle or not."
  {:arglists '([batch-db tid] [batch-db tid id-extractor matcher])}
  ([batch-db tid]
   (resource-handle-type-xf batch-db tid identity (fn [_ _] true)))
  ([{:keys [snapshot t since-t]} tid id-extractor matcher]
   (fn [rf]
     (let [target-buf (bb/allocate max-key-size)
           key-buf (bb/allocate max-key-size)
           value-buf (bb/allocate max-value-size)
           iter (kv/new-iterator snapshot :resource-as-of-index)]
       (fn
         ([result]
          (.close ^AutoCloseable iter)
          (rf result))
         ([result input]
          (if-let [handle (resource-handle* iter target-buf key-buf value-buf
                                            tid (id-extractor input) t since-t)]
            (if (matcher input handle)
              (rf result handle)
              result)
            result)))))))

(defn- encode-seek-key [tid]
  (-> (bb/allocate codec/tid-size)
      (bb/put-int! tid)
      bb/flip!
      (bs/from-byte-buffer!)))

(defn estimated-scan-size
  "Returns a relative estimation for the amount of work to do while scanning the
  ResourceAsOf index with the prefix consisting of `tid`.

  The metric is relative and unitless. It can be only used to compare the amount
  of scan work between different prefixes.

  Returns an anomaly if estimating the scan size isn't supported by `kv-store`."
  [kv-store tid]
  (let [seek-key (encode-seek-key tid)
        key-range [seek-key (bs/concat seek-key (bs/from-hex "FF"))]]
    (kv/estimate-scan-size kv-store :resource-as-of-index key-range)))
