(ns blaze.db.impl.index.resource
  "Implementation of a resource which lazily loads its content when needed."
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.protocols :as p]
    [blaze.db.kv :as kv]
    [blaze.fhir.util :as fhir-util])
  (:import
    [clojure.lang IMeta IPersistentMap ILookup]
    [java.util Arrays])
  (:refer-clojure :exclude [hash]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


;; Used as cache key. Implements equals on top of the byte array of a hash.
(deftype Hash [^bytes hash]
  Object
  (equals [this other]
    (if (identical? this other)
      true
      (if (or (nil? other) (not= Hash (class other)))
        false
        (Arrays/equals ^bytes hash ^bytes (.hash ^Hash other)))))
  (hashCode [_]
    (Arrays/hashCode ^bytes hash)))


(defn tx [kv-store t]
  (when-let [v (kv/get kv-store :tx-success-index (codec/t-key t))]
    (codec/decode-tx v t)))


(deftype ResourceMeta [node type state t ^:volatile-mutable tx]
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
        (do (set! tx (blaze.db.impl.index.resource/tx (:kv-store node) t))
            tx))
      not-found))

  (count [_] 5))


(defn- enhance-content ^IPersistentMap [content t]
  (update content :meta assoc :versionId (str t)))


(defn- mk-meta [node type state t]
  (ResourceMeta. node (keyword "fhir" type) state t nil))


(deftype ResourceContentMeta
  [node hash ^long t ^:volatile-mutable ^IPersistentMap meta]

  IPersistentMap
  (valAt [_ key]
    (case key
      :versionId (str t)
      (if meta
        (.valAt meta key)
        (do (set! meta (.valAt ^ILookup (p/-get-content node hash) :meta {}))
            (.valAt meta key))))))


(deftype Resource
  [node type id hash ^long state ^long t content-meta
   ^:volatile-mutable ^IPersistentMap content
   ^:volatile-mutable meta]

  IPersistentMap
  (containsKey [_ key]
    (case key
      :id true
      :resourceType true
      :meta true
      (if content
        (.containsKey content key)
        (do (set! content (p/-get-content node hash))
            (.containsKey content key)))))

  (seq [_]
    (if content
      (.seq (enhance-content content t))
      (do (set! content (p/-get-content node hash))
          (.seq (enhance-content content t)))))

  (valAt [_ key]
    (case key
      :id id
      :resourceType type
      :meta content-meta
      (if content
        (.valAt content key)
        (do (set! content (p/-get-content node hash))
            (.valAt content key)))))

  (valAt [_ key not-found]
    (case key
      :id id
      :resourceType type
      :meta content-meta
      (if content
        (.valAt content key not-found)
        (do (set! content (p/-get-content node hash))
            (.valAt content key not-found)))))

  (count [_]
    (if content
      (count (enhance-content content t))
      (do (set! content (p/-get-content node hash))
          (count (enhance-content content t)))))

  (equiv [this other]
    (.equals this other))

  IMeta
  (meta [_]
    (if meta
      meta
      (do (set! meta (mk-meta node type state t))
          meta)))

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


(defn hash
  "returns the hash of the resource as byte-array."
  [^Resource resource]
  (.hash ^Hash (.hash resource)))


(defn deleted? [^Resource resource]
  (codec/deleted? (.state resource)))


(let [kvs (->> (fhir-util/resources)
               (map (fn [{:keys [type]}] [(codec/tid type) type]))
               (sort-by first))
      tid->idx (int-array (map first kvs))
      idx->type (object-array (map second kvs))]
  (defn- tid->type [^long tid]
    (let [idx (Arrays/binarySearch tid->idx tid)]
      (when (nat-int? idx)
        (aget idx->type idx)))))


(defn new-resource
  "Creates a new resource.

  The `node` will be used to lazily load the resource content."
  [node tid id hash state t]
  (Resource. node (tid->type tid) id (Hash. hash) state t
             (ResourceContentMeta. node hash t nil)
             nil nil))
