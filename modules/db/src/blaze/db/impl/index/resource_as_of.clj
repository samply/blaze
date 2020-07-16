(ns blaze.db.impl.index.resource-as-of
  "Provides two public functions: `type-list` and `system-list`."
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource :as resource]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv])
  (:import
    [blaze.db.impl.codec ResourceAsOfKV]
    [clojure.lang IReduceInit]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def ^:const ^:private ^long max-key-size
  (+ codec/tid-size codec/max-id-size codec/t-size))


(def ^:const ^:private ^long value-size
  (+ codec/hash-size codec/state-size))


(defn- key-reader [iter ^ByteBuffer kb]
  (fn [_] (kv/key iter (.clear kb))))


(defn- focus-id!
  "Reduces the limit of `kb` in order to hide the t and focus on id solely."
  [^ByteBuffer kb]
  (.limit kb (- (.limit kb) codec/t-size)))


(defn- copy-id!
  "Copies the id bytes from the key buffer into the id buffer.

  After the call, both buffers are ready to be compared for id."
  [^ByteBuffer kb ^ByteBuffer ib]
  (let [id-size (.remaining kb)]
    (.limit ib codec/max-id-size)
    (.put ib kb)
    (.position ib 0)
    (.limit ib id-size))
  (.limit kb (+ (.limit kb) codec/t-size)))


(defn- skip-id!
  "Does the same to `position` and `limit` of `kb` as `copy-id!` but doesn't
  copy anything."
  [^ByteBuffer kb ^ByteBuffer ib]
  (.position kb (+ (.position kb) (.remaining ib)))
  (.limit kb (+ (.limit kb) codec/t-size)))


(defn- id-marker
  "Compares the id part of the current key buffer with the id buffer which may
  contain previously seen ids.

  Returns true if the id changed over the previously seen one. Keeps the id
  buffer up to date."
  [^ByteBuffer kb ^ByteBuffer ib]
  (fn [_]
    (focus-id! kb)
    (cond
      (zero? (.limit ib))
      (do
        (copy-id! kb ib)
        false)

      (.equals kb ib)
      (do
        (skip-id! kb ib)
        false)

      :else
      (do
        (copy-id! kb ib)
        true))))


(defn- tid-marker [^ByteBuffer kb tid-box ^ByteBuffer ib id-marker]
  (fn [x]
    (let [tid (codec/get-tid! kb)
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
          (.limit ib 0)
          (id-marker x)
          true)))))


(defn- new-entry!
  "Creates a new `ResourceAsOfKV` entry."
  [tid ^ByteBuffer ib ^ByteBuffer vb ^long t]
  (ResourceAsOfKV.
    tid
    (codec/id (.array ib) 0 (.remaining ib))
    t
    (let [hash (byte-array codec/hash-size)] (.get vb hash) hash)
    (.getLong vb)))


(defn- type-entry-creator
  "Returns a function of no argument which creates a new `ResourceAsOfKV` entry
  if the `t` in `kb` at the time of invocation is less than or equal `base-t`.

  Uses `iter` to read the `hash` and `state` when needed. Supplied `tid` to the
  created entry."
  [tid iter kb ib base-t]
  (let [vb (ByteBuffer/allocateDirect value-size)]
    #(let [t (codec/get-t! kb)]
       (when (<= t ^long base-t)
         (kv/value iter (.clear vb))
         (new-entry! tid ib vb t)))))


(defn- system-entry-creator
  [tid-box iter ^ByteBuffer kb ib base-t]
  (let [vb (ByteBuffer/allocateDirect value-size)]
    #(let [t (codec/get-t! kb)]
       (when (<= t ^long base-t)
         (kv/value iter (.clear vb))
         (new-entry! @tid-box ib vb t)))))


(defn- group-by-id
  "Returns a stateful transducer which takes flags from `id-marker` and supplies
  `ResourceAsOfKV` entries to the reduce function after id changes happen."
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


(defn- new-resource [node]
  (fn [^ResourceAsOfKV entry]
    (resource/new-resource node (.tid entry) (.id entry) (.hash entry) (.state entry)
                           (.t entry))))


(defn- start-key [tid start-id t]
  (if start-id
    (codec/resource-as-of-key tid start-id t)
    (codec/resource-as-of-key tid)))


(defn type-list
  "Returns a reducible collection of all resources of type with `tid` ordered
  by resource id.

  The list starts at the optional `start-id`.

  The resource-as-of-index consists of keys with three parts: `tid`, `id` and
  `t`. The `tid` is a 4-byte hash of the resource type, the `id` a variable
  length byte array of the resource id and `t` is an 8-byte long of the
  transaction number. The value of the resource-as-of-index contains two parts:
  `hash` and `state`. The `hash` is a 32-byte content hash of the resource and
  the `state` is an 8-byte long encoding create, put, delete state and a local
  version counter of the resource.

  The resource-as-of-index contains one entry for each resource version. For the
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

  The implementation iterates over the resource-as-of-index, starting this `tid`
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

  First the whole entry consisting of its key and value are decoded completely
  into an immutable data structure. Than the `tid` is tested against the given
  `tid` in order to stop iterating before hitting the next type. Third all
  entries are partitioned by id which each partition containing a list of
  entries with the same id. Last the entry with the right `t` is picked.

  This non-optimized implementation has several disadvantages. First each entry
  is decoded fully but the `t`, `hash` and `state` part is only needed from
  fewer entries. Even the `id` has only to be compared to a reference `id`, but
  not fully decoded, for non-matching entries. Second `partition-by` creates an
  vector of all entries with the same id. This leads to more object allocation
  and time spend.

  The implementation used here avoids excessive object allocation altogether.
  Only objects

  Uses three byte buffers to avoid object allocation as much as
  possible. The first buffer is a off-heap key buffer. Each key is read into
  the key buffer. The second buffer is an heap allocated id buffer. The id bytes
  of the first key are copied into the id buffer and later copied only on id
  change. To detect id changes, the id part of the key buffer and the id buffer
  are compared. When a resource is created the heap allocated byte array of the
  id buffer is fed into the string constructor. The string constructor will copy
  the bytes from the id buffer for immutability. So for each returned resource,
  a String and the internal byte array, it used, are allocated. The second byte
  array which is allocated for each resource is the byte array of the hash. No
  further byte arrays are allocated during the whole iteration. The state and t
  which are both longs are read from the off-heap key and value buffer. The hash
  and state which are read from the value buffer are only read once for each
  resource."
  ^IReduceInit
  [node raoi tid start-id t]
  (let [kb (ByteBuffer/allocateDirect max-key-size)
        ib (.limit (ByteBuffer/allocate codec/max-id-size) 0)
        entry-creator (type-entry-creator tid raoi kb ib t)]
    (coll/eduction
      (comp
        (map (key-reader raoi kb))
        (take-while (fn [_] (= ^int tid (codec/get-tid! kb))))
        (map (id-marker kb ib))
        (group-by-id entry-creator)
        (map (new-resource node))
        (remove resource/deleted?))
      (i/iter raoi (start-key tid start-id t)))))


(defn system-list
  "Returns a reducible collection of all resources ordered by resource tid and
  resource id.

  The list starts at the optional `start-tid` and `start-id`."
  ^IReduceInit
  [node raoi start-tid start-id t]
  (let [kb (ByteBuffer/allocateDirect max-key-size)
        tid-box (volatile! start-tid)
        ib (.limit (ByteBuffer/allocate codec/max-id-size) 0)
        entry-creator (system-entry-creator tid-box raoi kb ib t)]
    (coll/eduction
      (comp
        (map (key-reader raoi kb))
        (map (tid-marker kb tid-box ib (id-marker kb ib)))
        (group-by-id entry-creator)
        (map (new-resource node))
        (remove resource/deleted?))
      (if start-tid
        (i/iter raoi (start-key start-tid start-id t))
        (i/iter raoi)))))


(defn- instance-history-key-valid? [^long tid id ^long end-t]
  (fn [^ResourceAsOfKV kv]
    (and (= (.tid kv) tid) (= (.id kv) id) (< end-t (.t kv)))))


(defn instance-history
  "Returns a reducible collection of all versions between `start-t` (inclusive)
  and `end-t` (exclusive) of the resource with `tid` and `id`.

  Versions are resources itself."
  [node raoi tid id start-t end-t]
  (let [start-key (codec/resource-as-of-key tid (codec/id-bytes id) start-t)]
    (coll/eduction
      (comp
        (take-while (instance-history-key-valid? tid id end-t))
        (map (new-resource node)))
      (i/kvs raoi (codec/resource-as-of-kv-decoder) start-key))))


(defn- with-raoi-kv [raoi target fn]
  (kv/seek! raoi target)
  (when (kv/valid? raoi)
    ;; we have to check that we are still on target, because otherwise we would
    ;; find the next resource
    (let [k (kv/key raoi)]
      (when (codec/bytes-eq-without-t target k)
        (fn k (kv/value raoi))))))


(defn hash-state-t
  "Returns a triple of `hash`, `state` and `t` of the resource with `tid` and
  `id` at or before `t` if their is any."
  [raoi tid id t]
  (with-raoi-kv
    raoi (codec/resource-as-of-key tid id t)
    (fn [k v]
      [(codec/resource-as-of-value->hash v)
       (codec/resource-as-of-value->state v)
       (codec/resource-as-of-key->t k)])))


(defn resource
  "Returns a resource with `tid` and `id` at or before `t` if their is any.."
  [node raoi tid id t]
  (with-raoi-kv
    raoi (codec/resource-as-of-key tid id t)
    (fn [k v]
      (resource/new-resource
        node
        (codec/resource-as-of-key->tid k)
        (codec/id (codec/resource-as-of-key->id k))
        (codec/resource-as-of-value->hash v)
        (codec/resource-as-of-value->state v)
        (codec/resource-as-of-key->t k)))))


(defn- resource-state [raoi target]
  (with-raoi-kv raoi target (fn [_ v] (codec/resource-as-of-value->state v))))


(defn- num-of-instance-changes* ^long [iter tid id t]
  (-> (some-> (resource-state iter (codec/resource-as-of-key tid id t))
              codec/state->num-changes)
      (or 0)))


(defn num-of-instance-changes
  "Returns the number of changes between `start-t` (inclusive) and `end-t`
  (inclusive) of the resource with `tid` and `id`."
  [raoi tid id start-t end-t]
  (- (num-of-instance-changes* raoi tid id start-t)
     (num-of-instance-changes* raoi tid id end-t)))
