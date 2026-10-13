(ns blaze.db.resource-handle-cache
  "A cache from the type and id of a resource to the resource handle of its
  newest version.

  Resolving a resource handle by the type and id of a resource otherwise needs a
  seek in the ResourceAsOf index. The `t` isn't part of the key, so that entries
  stay valid over transactions.

  The fundamental principle of the cache is that an entry always holds the
  newest version of its resource. An entry is never outdated: it's either
  current or absent. So an entry is right for every database value whose `t`
  isn't older than the `t` of the cached handle, because no newer version
  exists. A database value with an older `t` can't use the entry and has to
  seek in the index instead. An absent entry is always safe, because then the
  reader seeks in the index as it would without the cache. All the rules below
  exist to keep this principle: the indexer updates present entries before a
  new `t` becomes visible to readers, and a reader inserts a handle only if
  it's sure that no newer version was written in the meantime.

  Reading:

  A reader with the database value at `t` uses an entry only if the `t` of the
  cached handle isn't newer than its own `t`. In that case the cached handle is
  the newest version overall and so also the right one for that database value.
  Otherwise the reader seeks the handle in the index.

  Admission:

  A reader inserts a key only on its second miss. A doorkeeper records the
  first miss of a key in a Bloom filter. Its window is measured in distinct
  keys that missed and is about the size of the cache in entries. A key that
  misses again only after more distinct keys than the cache can hold wouldn't
  have survived in the cache anyway. Without the doorkeeper, a scan over more
  resources than the cache holds would insert every resource it misses, and
  Caffeine would evict nearly all of them again. Caffeine does that eviction
  work under a single lock, which limits such a scan to the throughput of one
  core, and the churn evicts entries the scan would hit.

  The doorkeeper has a tumbling window: once the window is full, it replaces
  its Bloom filter by an empty one and forgets all keys recorded before. So the
  window doesn't slide, and how long a key is remembered depends on when its
  first miss falls in the window. A key whose second miss comes after `d` other
  distinct keys missed is inserted with a probability of about `1 - d/w` for a
  window of `w` keys, or not at all if `d` is at least `w`. On average, a key
  is remembered for half a window. The Bloom filter is a blocked one, so that
  a miss costs a single cache miss in it. It counts the distinct keys, and only
  a random sample of the new keys checks the count, because summing it is
  expensive.

  The doorkeeper only decides which keys are inserted, not how many. Inserted
  keys don't count towards the window, and the cache evicts as usual if more
  keys pass the doorkeeper than it can hold.

  Updating:

  The update of the indexer only changes entries already present in the cache.
  It never inserts the new versions a transaction writes, so that imports don't
  flood the cache with handles no query ever asked for. The entry of a purged
  resource is removed. The update of a transaction runs after its index entries
  were written and before its `t` gets visible to readers.

  The lookups the indexer does itself while expanding and verifying a
  transaction, like the ones of the referential integrity check, are the ones of
  a reader at the `t` before the transaction. So they insert through the
  doorkeeper like any other reader.

  Inserting:

  A reader that loaded a handle from an older snapshot must not insert it after
  the indexer skipped the missing entry, because the cache would then hold an
  outdated handle that looks valid for every later database value. So the
  indexer records the `t` of the last write of every key it updates or skips,
  the recent writes. A reader at `t` may insert a key only if no write of that
  key newer than `t` is recorded. It checks this inside a per-key atomic
  operation of the cache that is serialized with the one the indexer uses for
  its update and the recording.

  For a single key, the insert of a reader and the update of the indexer are
  serialized. If the reader comes first, the indexer finds the entry and
  replaces it. If the indexer comes first, the write is already recorded and
  the reader on the older `t` doesn't insert. A reader on an older `t` still
  inserts all the keys no later transaction wrote, so that the cache warms up
  under continuous writes.

  The recent writes are bounded. Once they hold more than `max-recent-writes`
  writes, the oldest transactions are pruned. Pruning first raises the floor to
  the `t` of the transaction and then removes its records, unless a newer write
  of the same key replaced them. A reader may only insert if its `t` is at
  least the floor, because the writes not newer than the floor aren't recorded
  anymore. The reader reads the record of its key before the floor, so that it
  sees the raised floor if it misses a record removed by pruning.

  The floor starts at the maximum, so that no reader inserts until the node set
  it to the `t` of the last transaction indexed before its start.

  Statistics:

  The cache records its hits and misses itself instead of Caffeine, because
  Caffeine counts every present entry as a hit, even if it's newer than the `t`
  of the reader, which has to seek the handle in the index then.

  Each node needs its own cache, because the recent writes belong to the
  transactions of a single node."
  (:refer-clojure :exclude [get])
  (:require
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.impl.cache :as cache]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.resource-handle-cache.spec]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig])
  (:import
   [blaze.db.impl.index Doorkeeper ResourceKey]
   [blaze.fhir.spec.type Base]
   [blaze.fhir.spec.type.system Strings]
   [com.github.benmanes.caffeine.cache Cache Caffeine Weigher]
   [com.github.benmanes.caffeine.cache.stats ConcurrentStatsCounter StatsCounter]
   [java.util ArrayDeque]
   [java.util.concurrent ConcurrentHashMap]
   [java.util.concurrent.atomic AtomicLong]
   [java.util.function BiFunction Function Supplier]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(deftype RecentWrites [^ConcurrentHashMap writes ^AtomicLong floor
                       ^ArrayDeque txs ^AtomicLong size ^long max-size])

(deftype ResourceHandleCache [^Cache cache ^RecentWrites recent-writes
                              ^Doorkeeper doorkeeper
                              ^StatsCounter stats-counter]
  ccp/StatsCache
  (-stats [_]
    (.stats cache))
  (-estimated-size [_]
    (.estimatedSize cache)))

(defn resource-handle-cache?
  "Returns true if `x` is a resource handle cache."
  [x]
  (instance? ResourceHandleCache x))

(defn- record-lookup!
  "Records the lookup of a reader in `cache` as hit if `handle` was found and as
  miss otherwise."
  [^ResourceHandleCache cache handle]
  (let [^StatsCounter stats-counter (.-stats-counter cache)]
    (if handle
      (.recordHits stats-counter 1)
      (.recordMisses stats-counter 1))))

(defn get
  "Returns the resource handle of the resource with `tid` and `id` from `cache`
  if it's the right one for a database value at `t`.

  Returns nil if the cache contains no handle or one newer than `t`."
  [^ResourceHandleCache cache tid id t]
  (let [handle (let [^Cache cache (.-cache cache)]
                 (.getIfPresent cache (ResourceKey. tid id)))
        handle (when (and handle (<= (long (:t handle)) (long t)))
                 handle)]
    (record-lookup! cache handle)
    handle))

(defn- insertable?
  "Returns true if a reader at `t` may insert an entry with `key`, because `t`
  is at least the floor and no write of `key` newer than `t` is recorded.

  Reads the record before the floor, so that a reader missing a record removed
  by pruning sees the floor raised before."
  [^RecentWrites recent-writes key ^long t]
  (let [write-t (.get ^ConcurrentHashMap (.-writes recent-writes) key)
        floor (.get ^AtomicLong (.-floor recent-writes))]
    (and (<= floor t) (or (nil? write-t) (<= (long write-t) t)))))

(defn- insert-fn
  "Inserts `handle` into an absent entry if a reader at `t` may insert it."
  [recent-writes ^long t handle]
  (reify Function
    (apply [_ key]
      (when (insertable? recent-writes key t)
        handle))))

(defn put!
  "Inserts `handle`, loaded by a reader with a database value at `t`, into
  `cache` if no entry is present, it missed before in the window of the
  doorkeeper and no write of it newer than `t` is recorded."
  [^ResourceHandleCache cache t handle]
  (let [recent-writes (.-recent-writes cache)
        key (ResourceKey/fromHandle handle)]
    ;; checks without a lock first, to skip the atomic operation if it can't
    ;; insert anyway; the check inside it stays for the race with the indexer
    (when (and (.seen ^Doorkeeper (.-doorkeeper cache) handle)
               (insertable? recent-writes key t))
      (let [^Cache caffeine-cache (.-cache cache)]
        (.computeIfAbsent (.asMap caffeine-cache) key
                          (insert-fn recent-writes t handle)))))
  nil)

(defn set-floor!
  "Sets the floor of `cache` to `t`, the `t` of the last transaction indexed
  before the start of the node."
  [^ResourceHandleCache cache t]
  (let [^RecentWrites recent-writes (.-recent-writes cache)]
    (.set ^AtomicLong (.-floor recent-writes) (long t))))

(defn- updated-handle
  "Returns the entry replacing `present-handle` for the update of `handle`.

  A purged `handle` removes the present entry unless it's newer.

  Otherwise, `handle` replaces the present entry with the id instance of the
  present handle, so that the id stays shared with the key. The id of the key
  isn't available here, because Caffeine calls the function with the key given
  to `compute` and not with the stored key. The present handle shares its id
  with the stored key, because `put!` creates the key from the id of the handle
  it inserts."
  [handle purged? present-handle]
  (if purged?
    (when (< (long (:t handle)) (long (:t present-handle)))
      present-handle)
    (rh/with-id handle (:id present-handle))))

(defn- update-fn
  "Records the write of the transaction with `t` and updates a present entry
  for the update of `handle`. Keeps an absent entry absent."
  [^ConcurrentHashMap writes t handle purged?]
  (reify BiFunction
    (apply [_ key present-handle]
      (.put writes key t)
      (when present-handle
        (updated-handle handle purged? present-handle)))))

(defn- update-entry!
  "Updates the entry of `update` of the transaction with `t` and returns its
  key."
  [^Cache cache writes t [handle purged?]]
  (let [key (ResourceKey/fromHandle handle)]
    (.compute (.asMap cache) key (update-fn writes t handle purged?))
    key))

(defn- raise-floor!
  "Raises the floor of `recent-writes` to `t` if it's lower."
  [^RecentWrites recent-writes ^long t]
  (let [^AtomicLong floor (.-floor recent-writes)]
    (when (< (.get floor) t)
      (.set floor t))))

(defn- prune!
  "Prunes the oldest transactions of `recent-writes` while it records more
  writes than its maximum size.

  Raises the floor to the `t` of a transaction before removing its records,
  keeping the records of newer writes of the same keys."
  [^RecentWrites recent-writes]
  (let [^ConcurrentHashMap writes (.-writes recent-writes)
        ^ArrayDeque txs (.-txs recent-writes)
        ^AtomicLong size (.-size recent-writes)]
    (while (< (.-max-size recent-writes) (.get size))
      (let [[t keys] (.poll txs)]
        (raise-floor! recent-writes t)
        (run! #(.remove writes % t) keys)
        (.addAndGet size (- (count keys)))))))

(defn update!
  "Updates the entries of `cache` present for the `updates` of the transaction
  with `t` and records the writes of all of them.

  Each update is a tuple of the resource handle of a ResourceAsOf index entry
  the transaction wrote and whether that entry is purged. A purged entry removes
  the present entry if it isn't older.

  Has to be called by a single thread only, because it prunes the recorded
  writes.

  Doesn't record a transaction without updates, because pruning counts writes
  and so would never remove it."
  [^ResourceHandleCache cache t updates]
  (when (seq updates)
    (let [^RecentWrites recent-writes (.-recent-writes cache)
          writes (.-writes recent-writes)
          keys (mapv (partial update-entry! (.-cache cache) writes t) updates)]
      (.add ^ArrayDeque (.-txs recent-writes) [t keys])
      (.addAndGet ^AtomicLong (.-size recent-writes) (count keys))
      (prune! recent-writes))))

(defn- align
  "Rounds `size` up to the object alignment of 8 bytes."
  ^long [^long size]
  (bit-and (+ size 7) -8))

(def ^:private entry-overhead
  "The estimated number of bytes of a cache entry without the bytes of the id
  String.

  Not a constant, because the reference size depends on the maximum heap size
  of the running JVM.

  ResourceKey:
    8 byte - object header
    4 byte - tid
    4 or 8 byte - id reference

  ResourceHandle:
    8 byte - object header
    4 or 8 byte - id reference
    4 byte - tid
    2 x 8 byte - t and state
    4 x 8 byte - hash

  Caffeine node (PSMW):
    8 byte - object header
    4 x 4 or 8 byte - key, value and access order references
    3 x 4 byte - metadata, weight and policy weight

  ConcurrentHashMap node:
    8 byte - object header
    4 byte - hash
    3 x 4 or 8 byte - key, value and next references

  ConcurrentHashMap table:
    2 x 4 or 8 byte - references, on average with a load factor of 0.75

  Frequency sketch:
    12 byte - one to two long counters, on average, because the number of
              counters is the number of entries rounded up to a power of two

  For an id of 16 chars, an entry occupies 220 bytes with a reference size of
  4 bytes and 276 bytes with a reference size of 8 bytes. Measured with JOL on
  a full cache with compact object headers, an entry occupied 221 and 279 bytes."
  (let [ref Base/MEM_SIZE_REFERENCE
        header Base/MEM_SIZE_OBJECT_HEADER]
    (+ (align (+ header 4 ref))
       (align (+ header ref 4 16 32))
       (align (+ header (* 4 ref) 12))
       (align (+ header 4 (* 3 ref)))
       (* 2 ref)
       12)))

(defn weight
  "Returns the estimated number of bytes the entry of `handle` occupies."
  [handle]
  (+ (long entry-overhead) (Strings/memSize ^String (:id handle))))

(def ^:private weigher
  (reify Weigher
    (weigh [_ _ handle]
      (weight handle))))

(defn- eviction-stats-counter-supplier
  "Supplies a stats counter to Caffeine that records only the evictions into
  `stats-counter`.

  The cache records hits and misses itself. It never loads entries. Caffeine
  records the compute operations used to insert and update entries as loads
  otherwise."
  [^StatsCounter stats-counter]
  (reify Supplier
    (get [_]
      (reify StatsCounter
        (recordHits [_ _])
        (recordMisses [_ _])
        (recordLoadSuccess [_ _])
        (recordLoadFailure [_ _])
        (recordEviction [_ weight cause]
          (.recordEviction stats-counter weight cause))
        (snapshot [_]
          (.snapshot stats-counter))))))

(defmethod m/pre-init-spec :blaze.db/resource-handle-cache [_]
  (s/keys :req-un [::max-size-ratio]
          :opt-un [::max-recent-writes ::doorkeeper-window]))

(def ^:private ^:const default-max-recent-writes
  "About 32 MiB of recorded writes with resource ids of 16 chars, measured
  with JOL."
  262144)

(defn- default-doorkeeper-window
  "Returns the number of entries of ids with 16 chars that fit into
  `max-size-in-bytes`.

  Overestimates the number of entries the cache holds if the ids are longer,
  which only makes the doorkeeper admit a bit more generously."
  [max-size-in-bytes]
  (max 1 (quot (long max-size-in-bytes)
               (+ (long entry-overhead) (Strings/memSize "0123456789abcdef")))))

(defmethod ig/init-key :blaze.db/resource-handle-cache
  [_ {:keys [max-size-ratio max-recent-writes doorkeeper-window]
      :or {max-recent-writes default-max-recent-writes}}]
  (let [max-size-in-bytes (cache/max-size-in-bytes "resource handle cache"
                                                   max-size-ratio)
        stats-counter (ConcurrentStatsCounter.)]
    (->ResourceHandleCache
     (-> (Caffeine/newBuilder)
         (.weigher weigher)
         (.maximumWeight max-size-in-bytes)
         (.recordStats (eviction-stats-counter-supplier stats-counter))
         (.build))
     (->RecentWrites (ConcurrentHashMap.) (AtomicLong. Long/MAX_VALUE)
                     (ArrayDeque.) (AtomicLong.) max-recent-writes)
     (Doorkeeper. (or doorkeeper-window
                      (default-doorkeeper-window max-size-in-bytes)))
     stats-counter)))
