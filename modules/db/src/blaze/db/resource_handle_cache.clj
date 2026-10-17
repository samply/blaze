(ns blaze.db.resource-handle-cache
  "A cache from the type and id of a resource to the resource handle of its
  newest version.

  Resolving a resource handle by the type and id of a resource otherwise needs a
  seek in the ResourceAsOf index. The `t` isn't part of the key, so that entries
  stay valid over transactions.

  Reading:

  A reader with the database value at `t` uses an entry only if the `t` of the
  cached handle isn't newer than its own `t`. In that case the cached handle is
  the newest version overall and so also the right one for that database value.
  Otherwise the reader seeks the handle in the index.

  Updating:

  The indexer only updates entries already present in the cache. It never
  inserts, so that imports don't flood the cache with handles no query ever
  asked for. The entry of a purged resource is removed. The update of a
  transaction runs after its index entries were written and before its `t` gets
  visible to readers.

  Inserting:

  A reader that loaded a handle from an older snapshot must not insert it after
  the indexer skipped the missing entry, because the cache would then hold an
  outdated handle that looks valid for every later database value. This is
  prevented by a gate. The indexer keeps the `t` of the newest transaction whose
  cache update has started, the update-t. It's set before the first entry of
  that transaction is updated. A reader may insert only if its own `t` is at
  least the update-t. It checks the update-t inside a per-key atomic operation
  of the cache that is serialized with the one the indexer uses for its update.

  For a single key, the insert of a reader and the update of the indexer are
  serialized. If the reader comes first, the indexer finds the entry and
  replaces it. If the indexer comes first, the update-t is already set and the
  reader on the older `t` doesn't insert.

  The update-t starts at the maximum, so that no reader inserts until the node
  set it to the `t` of the last transaction indexed before its start.

  Statistics:

  The cache records its hits and misses itself instead of Caffeine, because
  Caffeine counts every present entry as a hit, even if it's newer than the `t`
  of the reader, which has to seek the handle in the index then. Only the
  lookups of the readers are recorded, not the ones of the indexer, because the
  indexer never inserts and so misses every resource created.

  Each node needs its own cache, because the update-t belongs to the
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
   [blaze.db.impl.index ResourceKey]
   [blaze.fhir.spec.type Base]
   [blaze.fhir.spec.type.system Strings]
   [com.github.benmanes.caffeine.cache Cache Caffeine Policy$Eviction Weigher]
   [com.github.benmanes.caffeine.cache.stats ConcurrentStatsCounter StatsCounter]
   [java.util.concurrent.atomic AtomicLong]
   [java.util.function BiFunction Function Supplier]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(deftype ResourceHandleCache [^Cache cache ^AtomicLong update-t
                              ^StatsCounter stats-counter reader?]
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
  (when (.-reader? cache)
    (let [^StatsCounter stats-counter (.-stats-counter cache)]
      (if handle
        (.recordHits stats-counter 1)
        (.recordMisses stats-counter 1)))))

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

(defn- insert-fn
  "Inserts `handle` into an absent entry if `t` is at least the update-t."
  [^AtomicLong update-t ^long t handle]
  (reify Function
    (apply [_ _]
      (when (<= (.get update-t) t)
        handle))))

(defn put!
  "Inserts `handle`, loaded by a reader with a database value at `t`, into
  `cache` if no entry is present and `t` is at least the update-t.

  Doesn't insert anything into a read-only view of a cache."
  [^ResourceHandleCache cache t handle]
  (let [^AtomicLong update-t (.-update-t cache)]
    ;; checks the update-t without a lock first, to skip the atomic operation
    ;; if it can't insert anyway; the check inside it stays for the race with
    ;; the indexer
    (when (and (.-reader? cache) (<= (.get update-t) (long t)))
      (let [^Cache caffeine-cache (.-cache cache)]
        (.computeIfAbsent (.asMap caffeine-cache)
                          (ResourceKey. (:tid handle) (:id handle))
                          (insert-fn update-t t handle)))))
  nil)

(defn read-only
  "Returns a view of `cache` that reads its entries but doesn't insert into it
  and doesn't record its lookups as hits or misses.

  Used by the lookups the indexer does itself while expanding and verifying a
  transaction, because otherwise every updated resource would end up in the
  cache."
  [^ResourceHandleCache cache]
  (ResourceHandleCache. (.-cache cache) (.-update-t cache)
                        (.-stats-counter cache) false))

(defn set-update-t!
  "Sets the update-t of `cache` to `t`."
  [^ResourceHandleCache cache t]
  (let [^AtomicLong update-t (.-update-t cache)]
    (.set update-t (long t))))

(defn- remove-fn
  "Removes the present entry unless it's newer than `purged-handle`."
  [purged-handle]
  (reify BiFunction
    (apply [_ _ handle]
      (when (< (long (:t purged-handle)) (long (:t handle)))
        handle))))

(defn- replace-fn
  "Replaces the present entry by `handle` with the id instance of the present
  handle, so that the id stays shared with the key.

  The id of the key isn't available here, because Caffeine calls the function
  with the key given to `computeIfPresent` and not with the stored key. The
  present handle shares its id with the stored key, because `put!` creates the
  key from the id of the handle it inserts."
  [handle]
  (reify BiFunction
    (apply [_ _ present-handle]
      (rh/with-id handle (:id present-handle)))))

(defn- update-entry! [^Cache cache [handle purged?]]
  (.computeIfPresent (.asMap cache) (ResourceKey. (:tid handle) (:id handle))
                     (if purged? (remove-fn handle) (replace-fn handle))))

(defn update!
  "Updates the entries of `cache` present for the `updates` of the transaction
  with `t`.

  Each update is a tuple of the resource handle of a ResourceAsOf index entry
  the transaction wrote and whether that entry is purged. A purged entry removes
  the present entry if it isn't older.

  Sets the update-t to `t` before the first entry is updated."
  [^ResourceHandleCache cache t updates]
  (set-update-t! cache t)
  (run! (partial update-entry! (.-cache cache)) updates))

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

(defn max-weight
  "Returns the maximum weight of `cache` in bytes."
  [^ResourceHandleCache cache]
  (let [^Cache caffeine-cache (.-cache cache)
        ^Policy$Eviction eviction (.get (.eviction (.policy caffeine-cache)))]
    (.getMaximum eviction)))

(defmethod m/pre-init-spec :blaze.db/resource-handle-cache [_]
  (s/keys :req-un [::max-size-ratio]))

(defmethod ig/init-key :blaze.db/resource-handle-cache
  [_ {:keys [max-size-ratio]}]
  (let [max-size-in-bytes (cache/max-size-in-bytes "resource handle cache"
                                                   max-size-ratio)
        stats-counter (ConcurrentStatsCounter.)]
    (ResourceHandleCache.
     (-> (Caffeine/newBuilder)
         (.weigher weigher)
         (.maximumWeight max-size-in-bytes)
         (.recordStats (eviction-stats-counter-supplier stats-counter))
         (.build))
     (AtomicLong. Long/MAX_VALUE)
     stats-counter
     true)))
