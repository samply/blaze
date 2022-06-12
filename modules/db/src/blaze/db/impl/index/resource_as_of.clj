(ns blaze.db.impl.index.resource-as-of
  "Functions for accessing the ResourceAsOf index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.fhir.hash :as hash])
  (:import
    [com.github.benmanes.caffeine.cache Cache]
    [com.google.common.primitives Ints]
    [java.util.function Function]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long tid-did-size
  (+ codec/tid-size codec/did-size))


(def ^:private ^:const ^long key-size
  (+ codec/tid-size codec/did-size codec/t-size))


(def ^:private ^:const ^long max-value-size
  (+ hash/size codec/state-size codec/max-id-size))


(defn- key-reader [iter kb]
  (fn [_]
    (bb/clear! kb)
    (kv/key! iter kb)))


(defn- did-marker
  "Compares the did part of the current key buffer with the did volatile that
  may contain previously seen dids.

  Returns true if the did changed over the previously seen one. Keeps the did
  volatile up to date."
  [kb did-box]
  (fn [_]
    (if (nil? @did-box)
      (do
        (vreset! did-box (bb/get-long! kb))
        false)

      (let [did (bb/get-long! kb)]
        (if (= did @did-box)
          false
          (do
            (vreset! did-box did)
            true))))))


(defn- tid-marker [kb tid-box did-box id-marker]
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
          (vreset! did-box nil)
          (id-marker x)
          true)))))


(defn- new-entry!
  "Creates a new resource handle entry."
  [tid did-box vb t]
  (rh/resource-handle tid @did-box t vb))


(defn- type-entry-creator
  "Returns a function of no argument which creates a new resource handle entry
  if the `t` in `kb` at the time of invocation is less than or equal `base-t`.

  Uses `iter` to read the `hash` and `state` when needed. Supplies `tid` to the
  created entry."
  [tid iter kb did-box base-t]
  (let [vb (bb/allocate-direct max-value-size)]
    #(let [t (codec/descending-long (bb/get-5-byte-long! kb))]
       (when (<= t (unchecked-long base-t))
         (bb/clear! vb)
         (kv/value! iter vb)
         (new-entry! tid did-box vb t)))))


(defn- system-entry-creator
  [tid-box iter kb did-box base-t]
  (let [vb (bb/allocate-direct max-value-size)]
    #(let [t (codec/descending-long (bb/get-5-byte-long! kb))]
       (when (<= t (unchecked-long base-t))
         (bb/clear! vb)
         (kv/value! iter vb)
         (new-entry! @tid-box did-box vb t)))))


(defn- group-by-id
  "Returns a stateful transducer which takes flags from `did-marker` and
  supplies resource handle entries to the reduce function after did changes
  happen."
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


(defn- encode-key-buf [tid did t]
  (-> (bb/allocate key-size)
      (bb/put-int! tid)
      (bb/put-long! did)
      (bb/put-5-byte-long! (codec/descending-long t))))


(defn encode-key
  "Encodes the key of the ResourceAsOf index from `tid`, `id` and `t`."
  [tid did t]
  (bb/array (encode-key-buf tid did t)))


(defn- starts-with-tid? [^long tid kb]
  (fn [_] (= tid (bb/get-int! kb))))


(def ^:private remove-deleted-xf
  (remove rh/deleted?))


(defn- type-list-xf [{:keys [raoi t]} tid]
  (let [kb (bb/allocate-direct key-size)
        did-box (volatile! nil)
        entry-creator (type-entry-creator tid raoi kb did-box t)]
    (comp
      (map (key-reader raoi kb))
      (take-while (starts-with-tid? tid kb))
      (map (did-marker kb did-box))
      (group-by-id entry-creator)
      remove-deleted-xf)))


(defn- start-key
  ([tid]
   (-> (Ints/toByteArray tid) bs/from-byte-array))
  ([tid start-did t]
   (-> (encode-key-buf tid start-did t)
       bb/flip!
       bs/from-byte-buffer!)))


(defn type-list
  "Returns a reducible collection of all resource handles of type with `tid`
  ordered by resource did.

  The list starts at the optional `start-did`.

  The ResourceAsOf index consists of keys with three parts: `tid`, `did` and
  `t`. The `tid` is a 4-byte hash of the resource type, the `did` a 8-byte long
  of the resource did and `t` is an 8-byte long of the transaction number. The
  value of the ResourceAsOf index contains three parts: `hash`, `state` and
  `id`. The `hash` is a 32-byte content hash of the resource, the `state` is an
  8-byte long encoding create, put, delete state and a local version counter of
  the resource and the `id` is the resource id.

  The ResourceAsOf index contains one entry for each resource version. For the
  type list, only that versions not newer than `t` are returned. For example, an
  index containing two versions of the same resource looks like this:

    < tid-0, did-0, t=2 > < hash-2, state-2, id-0 >
    < tid-0, did-0, t=1 > < hash-1, state-1, id-0 >

  Here the `tid` and `did` are the same, because the resource is the same and
  only the versions differ. The newer version has a `t` of `2` and the older
  version a `t` of `1`. `t` values are simply an incrementing transaction
  number. The content hashes and states also differ. A `type-list` call with a
  `t` of `2` should return the newest version of the resource, were a call with
  a `t` of `1` should return the older version.

  The implementation iterates over the ResourceAsOf index, starting with `tid`
  and possible `start-did`. It goes from higher `t` values to lower `t` values
  because they are encoded in descending order.

  For each entry in the index, the following things are done:

   * check if the end of the index is reached
   * check if the first 4 bytes are still the same `tid` as given
   * for each `did` bytes seen over multiple entries, return that entry with
     `t` less than or equal to the `t` given

  A non-optimized implementation would use the following transducer, assuming
  the start with a collection of index entries starting at the right entry and
  ending at the end of the index:

    (comp
      (map decode-entry)
      (take-while tid-valid?)
      (partition-by did)
      (map pick-entry-according-to-t))

  First, the whole entry consisting of its key and value are decoded completely
  into an immutable data structure. Than the `tid` is tested against the given
  `tid` in order to stop iterating before hitting the next type. Third, all
  entries are partitioned by did which each partition containing a list of
  entries with the same did. Last, the entry with the right `t` is picked.

  This non-optimized implementation has several disadvantages. First, each entry
  is decoded fully but the `t`, `hash` and `state` part is only needed from
  fewer entries. Second `partition-by` creates an vector of all entries with the
  same sid. This leads to more object allocation and time spend.

  The implementation used here avoids excessive object allocation altogether.

  Uses two byte buffers and one volatile to avoid object allocation as much as
  possible. The first buffer is an off-heap key buffer. Each key is read into
  the key buffer. To detect did changes, the did is parsed from the key buffer
  and compared with the did from the volatile. The second byte array which is
  allocated for each resource handle is the byte array of the hash. No further
  byte arrays are allocated during the whole iteration. The state and t which
  are both longs are read from the off-heap key and value buffer. The hash and
  state which are read from the value buffer are only read once for each
  resource handle."
  ([{:keys [raoi] :as context} tid]
   (coll/eduction
     (type-list-xf context tid)
     (i/iter! raoi (start-key tid))))
  ([{:keys [raoi t] :as context} tid start-did]
   (coll/eduction
     (type-list-xf context tid)
     (i/iter! raoi (start-key tid start-did t)))))


(defn- system-list-xf [{:keys [raoi t]} start-tid]
  (let [kb (bb/allocate-direct key-size)
        tid-box (volatile! start-tid)
        did-box (volatile! nil)]
    (comp
      (map (key-reader raoi kb))
      (map (tid-marker kb tid-box did-box (did-marker kb did-box)))
      (group-by-id (system-entry-creator tid-box raoi kb did-box t))
      remove-deleted-xf)))


(defn system-list
  "Returns a reducible collection of all resource handles ordered by tid and
  did.

  The list starts at the optional `start-tid` and `start-did`."
  ([{:keys [raoi] :as context}]
   (coll/eduction
     (system-list-xf context nil)
     (i/iter! raoi)))
  ([{:keys [raoi t] :as context} start-tid start-did]
   (coll/eduction
     (system-list-xf context start-tid)
     (i/iter! raoi (start-key start-tid start-did t)))))


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
  (fn
    ([]
     [(bb/allocate-direct key-size)
      (bb/allocate-direct max-value-size)])
    ([kb vb]
     (rh/resource-handle
       (bb/get-int! kb)
       (bb/get-long! kb)
       (codec/descending-long (bb/get-5-byte-long! kb))
       vb))))


(defn- instance-history-key-valid? [^long tid ^long did ^long end-t]
  (fn [resource-handle]
    (and (= (rh/tid resource-handle) tid)
         (= (rh/did resource-handle) did)
         (< end-t (rh/t resource-handle)))))


(defn instance-history
  "Returns a reducible collection of all versions between `start-t` (inclusive)
  and `end-t` (exclusive) of the resource with `tid` and `did`.

  Versions are resource handles."
  [raoi tid did start-t end-t]
  (coll/eduction
    (take-while (instance-history-key-valid? tid did end-t))
    (i/kvs! raoi (decoder) (start-key tid did start-t))))


(defn- resource-handle* [raoi tb kb vb tid did t]
  ;; fill target buffer
  (bb/clear! tb)
  (bb/put-int! tb tid)
  (bb/put-long! tb did)
  (bb/put-5-byte-long! tb (codec/descending-long t))
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
    (bb/set-limit! tb tid-did-size)
    ;; focus key buffer on tid and id
    (bb/set-limit! kb tid-did-size)
    (when (= tb kb)
      ;; focus key buffer on t
      (bb/set-position! kb tid-did-size)
      (bb/set-limit! kb key-size)
      ;; read value
      (bb/clear! vb)
      (kv/value! raoi vb)
      ;; create resource handle
      (rh/resource-handle
        tid
        did
        (codec/descending-long (bb/get-5-byte-long! kb))
        vb))))


;; For performance reasons, we use that special Key class instead of a a simple
;; triple vector
(deftype Key [^long tid ^long did ^long t]
  Object
  (equals [_ x]
    ;; skip the instanceof check, because keys are only compared to other keys
    (and (= tid (.-tid ^Key x))
         (= did (.-did ^Key x))
         (= t (.-t ^Key x))))
  (hashCode [_]
    (-> (Long/hashCode tid)
        (unchecked-multiply-int 31)
        (unchecked-add-int (Long/hashCode did))
        (unchecked-multiply-int 31)
        (unchecked-add-int (Long/hashCode t)))))


(defn resource-handle
  "Returns a function which can be called with a `tid`, an `id` and an optional
  `t` which will lookup the resource handle in `raoi` using `rh-cache` as cache.

  The `t` is the default if `t` isn't given at the returned function.

  The returned function can't be called concurrently."
  [rh-cache raoi t]
  (let [tb (bb/allocate-direct key-size)
        kb (bb/allocate-direct key-size)
        vb (bb/allocate-direct max-value-size)
        rh (reify Function
             (apply [_ key]
               (resource-handle* raoi tb kb vb (.-tid ^Key key)
                                 (.-did ^Key key) (.-t ^Key key))))]
    (fn resource-handle
      ([tid did]
       (resource-handle tid did t))
      ([tid did t]
       (.get ^Cache rh-cache (Key. tid did t) rh)))))


(defn num-of-instance-changes
  "Returns the number of changes between `start-t` (inclusive) and `end-t`
  (inclusive) of the resource with `tid` and `did`."
  [resource-handle tid did start-t end-t]
  (- (long (:num-changes (resource-handle tid did start-t) 0))
     (long (:num-changes (resource-handle tid did end-t) 0))))
