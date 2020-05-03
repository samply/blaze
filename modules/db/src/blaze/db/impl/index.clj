(ns blaze.db.impl.index
  (:require
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.kv :as kv]
    [taoensso.nippy :as nippy])
  (:import
    [clojure.lang IMeta IPersistentMap IReduceInit]
    [com.github.benmanes.caffeine.cache LoadingCache]
    [java.io Closeable Writer]
    [java.util Arrays])
  (:refer-clojure :exclude [hash]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(deftype Hash [hash]
  Object
  (equals [this other]
    (if (identical? this other)
      true
      (if (or (nil? other) (not= Hash (class other)))
        false
        (bytes/= hash (.hash ^Hash other)))))
  (hashCode [_]
    (Arrays/hashCode ^bytes hash)))


(defn tx [kv-store t]
  (when-let [v (kv/get kv-store :tx-success-index (codec/t-key t))]
    (codec/decode-tx v t)))


(defn load-resource-content [kv-store ^Hash hash]
  (some-> (kv/get kv-store :resource-index (.hash hash)) (nippy/fast-thaw)))


(defn- enhance-content [content t]
  (update content :meta assoc :versionId (str t)))


(deftype ResourceMeta [kv-store type state t ^:volatile-mutable tx]
  IPersistentMap
  (valAt [this key]
    (.valAt this key nil))
  (valAt [_ key not-found]
    (case key
      :type type
      :blaze.db/t t
      :blaze.db/num-changes (codec/state->num-changes state)
      :blaze.db/op (codec/state->op state)
      :blaze.db/tx
      (if tx
        tx
        (do (set! tx (blaze.db.impl.index/tx kv-store t))
            tx))
      not-found))
  (count [_] 5))


(defn mk-meta [kv-store {:keys [resourceType]} state t]
  (ResourceMeta. kv-store (keyword "fhir" resourceType) state t nil))


(deftype Resource
  [kv-store ^LoadingCache cache id hash ^long state ^long t
   ^:volatile-mutable ^IPersistentMap content ^:volatile-mutable meta]

  IPersistentMap
  (containsKey [_ key]
    (case key
      :id true
      (if content
        (.containsKey content key)
        (do (set! content (enhance-content (.get cache hash) t))
            (.containsKey content key)))))
  (seq [_]
    (if content
      (.seq content)
      (do (set! content (enhance-content (.get cache hash) t))
          (.seq content))))
  (valAt [_ key]
    (case key
      :id id
      (if content
        (.valAt content key)
        (do (set! content (enhance-content (.get cache hash) t))
            (.valAt content key)))))
  (valAt [_ key not-found]
    (case key
      :id id
      (if content
        (.valAt content key not-found)
        (do (set! content (enhance-content (.get cache hash) t))
            (.valAt content key not-found)))))
  (count [_]
    (if content
      (count content)
      (do (set! content (enhance-content (.get cache hash) t))
          (count content))))

  IMeta
  (meta [_]
    (if meta
      meta
      (if content
        (do (set! meta (mk-meta kv-store content state t))
            meta)
        (do (set! content (enhance-content (.get cache hash) t))
            (set! meta (mk-meta kv-store content state t))
            meta))))

  Object
  (equals [this other]
    (if (identical? this other)
      true
      (if (or (nil? other) (not= Resource (class other)))
        false
        (and (= hash (.hash ^Resource other))
             (= t (.t ^Resource other))))))
  (hashCode [_]
    (-> (unchecked-multiply-int 31 (.hashCode hash))
        (unchecked-add-int t))))


(defn- hash [^Resource resource]
  (.hash ^Hash (.hash resource)))


(defmethod print-method Resource [^Resource resource ^Writer w]
  (.write w (format "Resource[id=%s, hash=%s, state=%d/%s, t=%d]"
                    (.id resource)
                    (codec/hex (hash resource))
                    (codec/state->num-changes (.state resource))
                    (name (codec/state->op (.state resource)))
                    (.t resource))))


(defn mk-resource
  [{:blaze.db/keys [kv-store resource-cache]} id hash state t]
  (Resource. kv-store resource-cache id (Hash. hash) state t nil nil))


(defn tx-success-entries [t tx-instant]
  [[:tx-success-index (codec/t-key t) (codec/encode-tx {:blaze.db.tx/instant tx-instant})]
   [:t-by-instant-index (codec/tx-by-instant-key tx-instant) (codec/encode-t t)]])


(defn tx-error-entries [t anom]
  [[:tx-error-index (codec/t-key t) (nippy/fast-freeze anom)]])


(defn- resource*** [context k v]
  (mk-resource
    context
    (codec/id (codec/resource-as-of-key->id k))
    (codec/resource-as-of-value->hash v)
    (codec/resource-as-of-value->state v)
    (codec/resource-as-of-key->t k)))


(defn- resource**
  [context resource-as-of-iter target]
  (when-let [k (kv/seek resource-as-of-iter target)]
    ;; we have to check that we are still on target, because otherwise we would
    ;; find the next resource
    (when (codec/bytes-eq-without-t target k)
      (resource*** context k (kv/value resource-as-of-iter)))))


(defn resource* [context resource-as-of-iter tid id t]
  (resource** context resource-as-of-iter (codec/resource-as-of-key tid id t)))


(defn- state-t** [k v]
  [(codec/resource-as-of-value->state v) (codec/resource-as-of-key->t k)])


(defn- state-t* [resource-as-of-iter target]
  (when-let [k (kv/seek resource-as-of-iter target)]
    ;; we have to check that we are still on target, because otherwise we would
    ;; find the next resource
    (when (codec/bytes-eq-without-t target k)
      (state-t** k (kv/value resource-as-of-iter)))))


(defn state-t
  "Returns a tuple of `state` and `t` of the resource with `tid` and `id` at or
  before `t`."
  [resource-as-of-iter tid id t]
  (state-t* resource-as-of-iter (codec/resource-as-of-key tid id t)))


(defn- resource-as-of-iter ^Closeable [snapshot]
  (kv/new-iterator snapshot :resource-as-of-index))


(defn resource [{:blaze.db/keys [kv-store] :as context} tid id t]
  (with-open [snapshot (kv/new-snapshot kv-store)
              i (resource-as-of-iter snapshot)]
    (resource* context i tid id t)))


(defn- resource-t**
  [resource-as-of-iter target]
  (when-let [k (kv/seek resource-as-of-iter target)]
    ;; we have to check that we are still on target, because otherwise we would
    ;; find the next resource
    (when (codec/bytes-eq-without-t target k)
      (codec/resource-as-of-key->t k))))


(defn resource-t* [resource-as-of-iter tid id t]
  (resource-t** resource-as-of-iter (codec/resource-as-of-key tid id t)))


(defn resource-state*
  [resource-as-of-iter target]
  (when-let [k (kv/seek resource-as-of-iter target)]
    ;; we have to check that we are still on target, because otherwise we would
    ;; find the next resource
    (when (codec/bytes-eq-without-t target k)
      (codec/resource-as-of-value->state (kv/value resource-as-of-iter)))))


(defn- t-by-instant*
  [t-by-instant-iter instant]
  (when (kv/seek t-by-instant-iter (codec/tx-by-instant-key instant))
    (codec/decode-t (kv/value t-by-instant-iter))))


(defn- t-by-instant-iter ^Closeable [snapshot]
  (kv/new-iterator snapshot :t-by-instant-index))


(defn t-by-instant
  [store instant]
  (with-open [snapshot (kv/new-snapshot store)
              i (t-by-instant-iter snapshot)]
    (t-by-instant* i instant)))


(defn- num-of-instance-changes* ^long [i tid id t]
  (-> (some-> (resource-state* i (codec/resource-as-of-key tid id t))
              codec/state->num-changes)
      (or 0)))


(defn num-of-instance-changes
  [{:blaze.db/keys [kv-store]} tid id start-t since-t]
  (with-open [snapshot (kv/new-snapshot kv-store)
              i (resource-as-of-iter snapshot)]
    (- (num-of-instance-changes* i tid id start-t)
       (num-of-instance-changes* i tid id since-t))))


(defn type-stats [i tid t]
  (when-let [k (kv/seek i (codec/type-stats-key tid t))]
    (when (= tid (codec/type-stats-key->tid k))
      (kv/value i))))


(defn- type-stats-iter ^Closeable [snapshot]
  (kv/new-iterator snapshot :type-stats-index))


(defn- num-of-type-changes* ^long [i tid t]
  (or (some-> (type-stats i tid t) codec/type-stats-value->num-changes) 0))


(defn num-of-type-changes
  [{:blaze.db/keys [kv-store]} tid start-t since-t]
  (with-open [snapshot (kv/new-snapshot kv-store)
              i (type-stats-iter snapshot)]
    (- (num-of-type-changes* i tid start-t)
       (num-of-type-changes* i tid since-t))))


(defn- system-stats-iter ^Closeable [snapshot]
  (kv/new-iterator snapshot :system-stats-index))


(defn system-stats [i t]
  (when (kv/seek i (codec/system-stats-key t))
    (kv/value i)))


(defn- num-of-system-changes* ^long [i t]
  (or (some-> (system-stats i t) codec/system-stats-value->num-changes) 0))


(defn num-of-system-changes
  [{:blaze.db/keys [kv-store]} start-t since-t]
  (with-open [snapshot (kv/new-snapshot kv-store)
              i (system-stats-iter snapshot)]
    (- (num-of-system-changes* i start-t)
       (num-of-system-changes* i since-t))))


(defn deleted? [^Resource resource]
  (codec/deleted? (.state resource)))


(defn- non-deleted-resource-resource [context raoi tid id t]
  (when-let [resource (resource* context raoi tid id t)]
    (when-not (deleted? resource)
      resource)))


(defn- type-list-move-to-t [iter start-k ^long t]
  (loop [k start-k]
    (when (and k (codec/bytes-eq-without-t k start-k))
      (if (< t ^long (codec/resource-as-of-key->t k))
        (recur (kv/next iter))
        k))))


(defn- type-list-seek [iter tid start-id t]
  (if start-id
    (kv/seek iter (codec/resource-as-of-key tid start-id t))
    (type-list-move-to-t iter (kv/seek iter (codec/resource-as-of-key tid)) t)))


(defn- resource-as-of-key-id= [^bytes k ^bytes start-k]
  (Arrays/equals k codec/tid-size (- (alength k) codec/t-size)
                 start-k codec/tid-size (- (alength start-k) codec/t-size)))


(defn- type-list-next [iter ^bytes start-k ^long t]
  (loop [k (kv/next iter)]
    (when (and k (bytes/prefix= k start-k codec/tid-size))
      (if (resource-as-of-key-id= k start-k)
        (recur (kv/next iter))
        (type-list-move-to-t iter k t)))))


(defn type-list
  "Returns a reducible collection of `HashStateT` records of all resources of
  type with `tid` ordered by resource id.

  The list starts at `start-id`."
  [{:blaze.db/keys [kv-store] :as context} tid start-id t]
  (let [key-prefix (codec/resource-as-of-key tid)]
    (reify
      IReduceInit
      (reduce [_ rf init]
        (with-open [snapshot (kv/new-snapshot kv-store)
                    iter (resource-as-of-iter snapshot)]
          (loop [ret init
                 k (type-list-seek iter tid start-id t)]
            (if (and k (bytes/prefix= key-prefix k codec/tid-size))
              (let [resource (resource*** context k (kv/value iter))]
                (if-not (deleted? resource)
                  (let [ret (rf ret resource)]
                    (if (reduced? ret)
                      @ret
                      (recur ret (type-list-next iter k t))))
                  (recur ret (type-list-next iter k t))))
              ret)))))))


(defn- compartment-list-start-key [{:keys [c-hash res-id]} tid start-id]
  (if start-id
    (codec/compartment-resource-type-key c-hash res-id tid start-id)
    (codec/compartment-resource-type-key c-hash res-id tid)))


(defn- compartment-list-cmp-key [{:keys [c-hash res-id]} tid]
  (codec/compartment-resource-type-key c-hash res-id tid))


(defn- key-valid? [^bytes start-key ^bytes key]
  (and (<= (alength start-key) (alength key))
       (bytes/prefix= start-key key (alength start-key))))


(defn compartment-list
  "Returns a reducible collection of `HashStateT` records of all resources
  linked to `compartment` and of type with `tid` ordered by resource id.

  The list starts at `start-id`.

  The implementation uses the :resource-type-index to obtain an iterator over
  all resources of the type with `tid` ever known (independent from `t`). It
  then looks up the newest version of each resource in the :resource-as-of-index
  not newer then `t`."
  [{:blaze.db/keys [kv-store] :as context} compartment tid start-id t]
  (let [start-key (compartment-list-start-key compartment tid start-id)
        cmp-key (compartment-list-cmp-key compartment tid)]
    (reify
      IReduceInit
      (reduce [_ rf init]
        (with-open [snapshot (kv/new-snapshot kv-store)
                    iter (kv/new-iterator snapshot :compartment-resource-type-index)
                    raoi (resource-as-of-iter snapshot)]
          (loop [ret init
                 k (kv/seek iter start-key)]
            (if (and k (key-valid? cmp-key k))
              (if-let [val (non-deleted-resource-resource
                             context raoi tid (codec/compartment-resource-type-key->id k) t)]
                (let [ret (rf ret val)]
                  (if (reduced? ret)
                    @ret
                    (recur ret (kv/next iter))))
                (recur ret (kv/next iter)))
              ret)))))))


(defn- resource-as-of-key-prefix= [^long tid ^bytes id ^long since-t k]
  (and (= tid (codec/resource-as-of-key->tid k))
       (bytes/= id (codec/resource-as-of-key->id k))
       (< since-t ^long (codec/resource-as-of-key->t k))))


(defn- instance-history-entry [context k v]
  (mk-resource
    context
    (codec/id (codec/resource-as-of-key->id k))
    (codec/resource-as-of-value->hash v)
    (codec/resource-as-of-value->state v)
    (codec/resource-as-of-key->t k)))


(defn instance-history
  "Returns a reducible collection of `HashStateT` records of instance history
  entries.

  The history starts at `t`."
  [{:blaze.db/keys [kv-store] :as context} tid id start-t since-t]
  (let [key-still-valid? (partial resource-as-of-key-prefix= tid id since-t)]
    (reify IReduceInit
      (reduce [_ rf init]
        (with-open [snapshot (kv/new-snapshot kv-store)
                    i (resource-as-of-iter snapshot)]
          (loop [ret init
                 k (kv/seek i (codec/resource-as-of-key tid id start-t))]
            (if (some-> k key-still-valid?)
              (let [ret (rf ret (instance-history-entry context k (kv/value i)))]
                (if (reduced? ret)
                  @ret
                  (recur ret (kv/next i))))
              ret)))))))


(defn- type-as-of-key-prefix= [^long tid ^long since-t k]
  (and (= tid (codec/type-as-of-key->tid k))
       (< since-t ^long (codec/type-as-of-key->t k))))


(defn type-as-of-key [tid start-t start-id]
  (if start-id
    (codec/type-as-of-key tid start-t start-id)
    (codec/type-as-of-key tid start-t)))


(defn- type-history-entry [context resource-as-of-iter tid k]
  (resource*
    context
    resource-as-of-iter
    tid
    (codec/type-as-of-key->id k)
    (codec/type-as-of-key->t k)))


(defn type-history
  "Returns a reducible collection of `HashStateT` records of type history
  entries.

  The history starts at `t`."
  [{:blaze.db/keys [kv-store] :as context} tid start-t start-id since-t]
  (let [key-still-valid? (partial type-as-of-key-prefix= tid since-t)]
    (reify IReduceInit
      (reduce [_ rf init]
        (with-open [snapshot (kv/new-snapshot kv-store)
                    i (kv/new-iterator snapshot :type-as-of-index)
                    ri (resource-as-of-iter snapshot)]
          (loop [ret init
                 k (kv/seek i (type-as-of-key tid start-t start-id))]
            (if (some-> k key-still-valid?)
              (let [ret (rf ret (type-history-entry context ri tid k))]
                (if (reduced? ret)
                  @ret
                  (recur ret (kv/next i))))
              ret)))))))


(defn- system-as-of-key-prefix= [^long since-t k]
  (< since-t ^long (codec/system-as-of-key->t k)))


(defn- system-as-of-key [start-t start-tid start-id]
  (cond
    start-id (codec/system-as-of-key start-t start-tid start-id)
    start-tid (codec/system-as-of-key start-t start-tid)
    :else (codec/system-as-of-key start-t)))


(defn- system-history-entry [context resource-as-of-iter k]
  (resource*
    context
    resource-as-of-iter
    (codec/system-as-of-key->tid k)
    (codec/system-as-of-key->id k)
    (codec/system-as-of-key->t k)))


(defn system-history
  "Returns a reducible collection of `HashStateT` records of system history
  entries.

  The history starts at `t`."
  [{:blaze.db/keys [kv-store] :as context} start-t start-tid start-id since-t]
  (let [key-still-valid? (partial system-as-of-key-prefix= since-t)]
    (reify IReduceInit
      (reduce [_ rf init]
        (with-open [snapshot (kv/new-snapshot kv-store)
                    i (kv/new-iterator snapshot :system-as-of-index)
                    ri (resource-as-of-iter snapshot)]
          (loop [ret init
                 k (kv/seek i (system-as-of-key start-t start-tid start-id))]
            (if (some-> k key-still-valid?)
              (let [ret (rf ret (system-history-entry context ri k))]
                (if (reduced? ret)
                  @ret
                  (recur ret (kv/next i))))
              ret)))))))


(defn- spv-resource
  "Given an `tid`, `id` and `t`, searches for the hash at `t` in `raoi` and
  checks back whether :search-param-value-index actually contains that hash.

  If both is true, the resource triple of the matching resource is returned."
  [context snapshot k raoi tid id t]
  (when-let [resource (resource* context raoi tid id t)]
    (when-not (deleted? resource)
      (codec/hash->search-param-value-key! (hash resource) k)
      (when (kv/snapshot-get snapshot :search-param-value-index k)
        resource))))


(defn- spv-resource* [context snapshot k raoi tid id clauses t]
  (when-let [resource (spv-resource context snapshot k raoi tid id t)]
    (loop [[[search-param value] & clauses] clauses]
      (if search-param
        (when (search-param/matches? search-param snapshot tid id (hash resource) value)
          (recur clauses))
        resource))))


(defn type-query [{:blaze.db/keys [kv-store] :as context} tid clauses t]
  (let [[[search-param value] & clauses] clauses]
    (reify IReduceInit
      (reduce [_ rf init]
        (with-open [snapshot (kv/new-snapshot kv-store)
                    raoi (resource-as-of-iter snapshot)
                    spi (search-param/new-iterator search-param snapshot tid value)]
          (loop [ret init
                 k (search-param/first spi)]
            (if k
              (let [id (codec/search-param-value-key->id k)]
                (if-let [resource (spv-resource* context snapshot k raoi tid id clauses t)]
                  (let [ret (rf ret resource)]
                    (if (reduced? ret)
                      @ret
                      (recur ret (search-param/next spi id))))
                  (recur ret (search-param/next spi id))))
              ret)))))))


(defn- compartment-spv-resource
  "Given an `tid`, `id` and `t`, searches for the most recent version of the
  resource in :resource-as-of-index and checks back whether
  :compartment-search-param-value-index actually contains the hash of that
  version.

  If both is true, the resource is returned."
  [context snapshot k resource-as-of-iter tid id t]
  (when-let [resource (resource* context resource-as-of-iter tid id t)]
    (when-not (deleted? resource)
      (codec/hash->compartment-search-param-value-key! (hash resource) k)
      (when (kv/snapshot-get snapshot :compartment-search-param-value-index k)
        resource))))


(defn- compartment-spv-resource* [context snapshot compartment k raoi tid id clauses t]
  (when-let [resource (compartment-spv-resource context snapshot k raoi tid id t)]
    (loop [[[search-param value] & clauses] clauses]
      (if search-param
        (when (search-param/compartment-matches? search-param snapshot compartment tid id (hash resource) value)
          (recur clauses))
        resource))))


(defn compartment-query
  [{:blaze.db/keys [kv-store] :as context} compartment tid clauses t]
  (let [[[search-param value] & clauses] clauses]
    (reify IReduceInit
      (reduce [_ rf init]
        (with-open [snapshot (kv/new-snapshot kv-store)
                    raoi (resource-as-of-iter snapshot)
                    spi (search-param/new-compartment-iterator search-param snapshot compartment tid value)]
          (loop [ret init
                 k (search-param/first spi)]
            (if k
              (let [id (codec/compartment-search-param-value-key->id k)]
                (if-let [resource (compartment-spv-resource* context snapshot compartment k raoi tid id clauses t)]
                  (let [ret (rf ret resource)]
                    (if (reduced? ret)
                      @ret
                      (recur ret (search-param/next spi id))))
                  (recur ret (search-param/next spi id))))
              ret)))))))


(defn- type-total* [i tid t]
  (or (some-> (type-stats i tid t) codec/type-stats-value->total) 0))


(defn type-total [{:blaze.db/keys [kv-store]} tid t]
  (with-open [snapshot (kv/new-snapshot kv-store)
              i (type-stats-iter snapshot)]
    (type-total* i tid t)))
