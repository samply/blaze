(ns blaze.db.impl.index.resource-as-of
  "Functions for accessing the ResourceAsOf index."
  (:require
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv])
  (:import
    [com.github.benmanes.caffeine.cache Cache]
    [java.util.function Function]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long max-key-size
  (+ codec/tid-size codec/max-id-size codec/t-size))


(def ^:private ^:const ^long value-size
  (+ codec/hash-size codec/state-size))


(defn- key-reader [iter kb]
  (fn [_]
    (bb/clear! kb)
    (kv/key! iter kb)))


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
  (bb/set-limit! kb (+ (bb/limit kb) codec/t-size)))


(defn- skip-id!
  "Does the same to `position` and `limit` of `kb` as `copy-id!` but doesn't
  copy anything."
  [kb ib]
  (bb/set-position! kb (+ (bb/position kb) (bb/remaining ib)))
  (bb/set-limit! kb (+ (bb/limit kb) codec/t-size)))


(defn- id-marker
  "Compares the id part of the current key buffer with the id buffer that may
  contain previously seen ids.

  Returns true if the id changed over the previously seen one. Keeps the id
  buffer up to date."
  [kb ib]
  (fn [_]
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


(defn- tid-marker [kb tid-box ib id-marker]
  (fn [x]
    (let [tid (bb/get-int! kb)
          last-tid @tid-box]
      (cond
        (nil? last-tid)
        (do
          (vreset! tid-box tid)
          (id-marker x))

        (= tid last-tid)
        (id-marker x)

        :else
        (do
          (vreset! tid-box tid)
          (bb/set-limit! ib 0)
          (id-marker x)
          true)))))


(defn- new-entry!
  "Creates a new resource handle entry."
  [tid ib vb t]
  (rh/resource-handle tid (codec/id (bb/array ib) 0 (bb/remaining ib)) t vb))


(defn- type-entry-creator
  "Returns a function of no argument which creates a new resource handle entry
  if the `t` in `kb` at the time of invocation is less than or equal `base-t`.

  Uses `iter` to read the `hash` and `state` when needed. Supplied `tid` to the
  created entry."
  [tid iter kb ib base-t]
  (let [vb (bb/allocate-direct value-size)]
    #(let [t (codec/descending-long (bb/get-long! kb))]
       (when (<= t ^long base-t)
         (bb/clear! vb)
         (kv/value! iter vb)
         (new-entry! tid ib vb t)))))


(defn- system-entry-creator
  [tid-box iter kb ib base-t]
  (let [vb (bb/allocate-direct value-size)]
    #(let [t (codec/descending-long (bb/get-long! kb))]
       (when (<= t ^long base-t)
         (bb/clear! vb)
         (kv/value! iter vb)
         (new-entry! @tid-box ib vb t)))))


(defn- group-by-id
  "Returns a stateful transducer which takes flags from `id-marker` and supplies
  resource handle entries to the reduce function after id changes happen."
  [entry-creator]
  (let [state (volatile! nil)
        search-entry! #(when-let [e (entry-creator)]
                         (vreset! state e))]
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
        ([result id-changed?]
         (if id-changed?
           (if-let [e @state]
             (let [result (rf result e)]
               (vreset! state nil)
               (when-not (reduced? result)
                 (search-entry!))
               result)
             (do
               (search-entry!)
               result))
           (if @state
             result
             (do
               (search-entry!)
               result))))))))


(defn- encode-key-buf [tid id t]
  (-> (bb/allocate (+ Integer/BYTES (bs/size id) Long/BYTES))
      (bb/put-int! tid)
      (bb/put-byte-string! id)
      (bb/put-long! (codec/descending-long ^long t))))


(defn encode-key
  "Encodes the key of the ResourceAsOf index from `tid`, `id` and `t`."
  [tid id t]
  (bb/array (encode-key-buf tid id t)))


(defn- type-list* [{:keys [raoi t]} tid start-key]
  (let [kb (bb/allocate-direct max-key-size)
        ib (bb/set-limit! (bb/allocate codec/max-id-size) 0)
        entry-creator (type-entry-creator tid raoi kb ib t)]
    (coll/eduction
      (comp
        (map (key-reader raoi kb))
        (take-while (fn [_] (= ^long tid (bb/get-int! kb))))
        (map (id-marker kb ib))
        (group-by-id entry-creator)
        (remove (comp #{:delete} :op)))
      (i/iter! raoi start-key))))


(defn- start-key
  ([tid]
   (-> (bb/allocate Integer/BYTES)
       (bb/put-int! tid)
       (bb/flip!)
       (bs/from-byte-buffer)))
  ([tid start-id t]
   (-> (encode-key-buf tid start-id t)
       (bb/flip!)
       (bs/from-byte-buffer))))


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
  ([context tid]
   (type-list* context tid (start-key tid)))
  ([{:keys [t] :as context} tid start-id]
   (type-list* context tid (start-key tid start-id t))))


(defn- system-list-xf [{:keys [raoi t]} start-tid]
  (let [kb (bb/allocate-direct max-key-size)
        tid-box (volatile! start-tid)
        ib (bb/set-limit! (bb/allocate codec/max-id-size) 0)]
    (comp
      (map (key-reader raoi kb))
      (map (tid-marker kb tid-box ib (id-marker kb ib)))
      (group-by-id (system-entry-creator tid-box raoi kb ib t))
      (remove (comp #{:delete} :op)))))


(defn system-list
  "Returns a reducible collection of all resource handles ordered by resource
  tid and resource id.

  The list starts at the optional `start-tid` and `start-id`."
  ([{:keys [raoi] :as context}]
   (coll/eduction
     (system-list-xf context nil)
     (i/iter! raoi)))
  ([{:keys [raoi t] :as context} start-tid start-id]
   (coll/eduction
     (system-list-xf context start-tid)
     (i/iter! raoi (start-key start-tid start-id t)))))


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
    (fn
      ([]
       [(bb/allocate-direct max-key-size)
        (bb/allocate-direct value-size)])
      ([kb vb]
       (rh/resource-handle
         (bb/get-int! kb)
         (let [id-size (- (bb/remaining kb) codec/t-size)]
           (bb/copy-into-byte-array! kb ib 0 id-size)
           (codec/id ib 0 id-size))
         (codec/descending-long (bb/get-long! kb))
         vb)))))


(defn- instance-history-key-valid? [tid id ^long end-t]
  (fn [resource-handle]
    (and (= (:tid resource-handle) tid)
         (= (:id resource-handle) id)
         (< end-t ^long (:t resource-handle)))))


(defn instance-history
  "Returns a reducible collection of all versions between `start-t` (inclusive)
  and `end-t` (exclusive) of the resource with `tid` and `id`.

  Versions are resource handles."
  [raoi tid id start-t end-t]
  (let [start-key (encode-key tid id start-t)]
    (coll/eduction
      (take-while (instance-history-key-valid? tid (codec/id-string id) end-t))
      (i/kvs! raoi (decoder) (bs/from-byte-array start-key)))))


(defn- resource-handle** [raoi tb kb vb tid id t]
  ;; fill target buffer
  (bb/clear! tb)
  (bb/put-int! tb tid)
  (bb/put-byte-string! tb id)
  (bb/put-long! tb (codec/descending-long ^long t))
  ;; flip target buffer to be ready for seek
  (bb/flip! tb)
  (kv/seek-buffer! raoi tb)
  (when (kv/valid? raoi)
    ;; read key
    (bb/clear! kb)
    (kv/key! raoi kb)
    ;; we have to check that we are still on target, because otherwise we
    ;; would find the next resource
    ;; focus target buffer on tid and id
    (bb/rewind! tb)
    (bb/set-limit! tb (- (bb/limit tb) codec/t-size))
    ;; focus key buffer on tid and id
    (bb/set-limit! kb (- (bb/limit kb) codec/t-size))
    (when (= tb kb)
      ;; focus key buffer on t
      (let [limit (bb/limit kb)]
        (bb/set-position! kb limit)
        (bb/set-limit! kb (+ limit codec/t-size)))
      ;; read value
      (bb/clear! vb)
      (kv/value! raoi vb)
      ;; create resource handle
      (rh/resource-handle
        tid
        (codec/id-string id)
        (codec/descending-long (bb/get-long! kb))
        vb))))


;; For performance reasons, we use that special Key class instead of a a simple
;; triple vector
(deftype Key [^long tid ^Object id ^long t]
  Object
  (equals [_ x]
    (and (instance? Key x)
         (= tid ^long (.-tid ^Key x))
         (.equals id (.-id ^Key x))
         (= t ^long  (.-t ^Key x))))
  (hashCode [_]
    (-> tid
        (unchecked-multiply-int 31)
        (unchecked-add-int (.hashCode id))
        (unchecked-multiply-int 31)
        (unchecked-add-int t))))


(defn resource-handle
  "Returns a function which can be called with a `tid`, an `id` and an optional
  `t` which will lookup the resource handle in `raoi`.

  The `t` is the default if `t` isn't given at the returned function."
  [rh-cache raoi t]
  (let [tb (bb/allocate-direct max-key-size)
        kb (bb/allocate-direct max-key-size)
        vb (bb/allocate-direct value-size)
        rh (reify Function
             (apply [_ key]
               (resource-handle** raoi tb kb vb (.-tid ^Key key)
                                  (.-id ^Key  key) (.-t ^Key key))))]
    (fn resource-handle
      ([tid id]
       (resource-handle tid id t))
      ([tid id t]
       (.get ^Cache rh-cache (Key. tid id t) rh)))))


(defn num-of-instance-changes
  "Returns the number of changes between `start-t` (inclusive) and `end-t`
  (inclusive) of the resource with `tid` and `id`."
  [resource-handle tid id start-t end-t]
  (- ^long (:num-changes (resource-handle tid id start-t) 0)
     ^long (:num-changes (resource-handle tid id end-t) 0)))
