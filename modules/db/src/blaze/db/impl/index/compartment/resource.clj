(ns blaze.db.impl.index.compartment.resource
  "Functions for accessing the CompartmentResourceType index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.iterators :as i]))

(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long except-co-res-id-prefix-size
  (+ codec/c-hash-size 1 codec/tid-size))

(defn- key-prefix-size
  {:inline
   (fn [co-res-id]
     `(unchecked-add-int ~except-co-res-id-prefix-size (bs/size ~co-res-id)))}
  [co-res-id]
  (unchecked-add-int except-co-res-id-prefix-size (bs/size co-res-id)))

(defn- decode-key! [buf]
  (bb/set-position! buf (unchecked-add-int (bb/position buf) codec/c-hash-size))
  (let [id-size (long (bb/size-up-to-null buf))]
    (bb/set-position! buf (+ (bb/position buf) id-size 1 codec/tid-size))
    (bs/from-byte-buffer! buf)))

(defn- resource-handle-xf [batch-db tid]
  (comp
   (rao/resource-handle-type-xf batch-db tid)
   (remove rh/deleted?)))

(defn- encode-seek-key
  "Encodes the key without the id used for seeking to the start of scans."
  [compartment tid]
  (let [co-c-hash (coll/nth compartment 0)
        co-res-id (coll/nth compartment 1)]
    (-> (bb/allocate (key-prefix-size co-res-id))
        (bb/put-int! co-c-hash)
        (bb/put-null-terminated-byte-string! co-res-id)
        (bb/put-int! tid)
        bb/flip!
        bs/from-byte-buffer!)))

(defn- encode-key-buf
  "Encodes the full key."
  [compartment tid id]
  (let [co-c-hash (coll/nth compartment 0)
        co-res-id (coll/nth compartment 1)]
    (-> (bb/allocate (unchecked-add-int (key-prefix-size co-res-id) (bs/size id)))
        (bb/put-int! co-c-hash)
        (bb/put-null-terminated-byte-string! co-res-id)
        (bb/put-int! tid)
        (bb/put-byte-string! id))))

(defn- encode-key
  "Encodes the full key."
  [compartment tid id]
  (-> (encode-key-buf compartment tid id) bb/flip! bs/from-byte-buffer!))

(defn resource-handles
  "Returns a reducible collection of resource handles from `batch-db` in
  `compartment` of type with `tid`, starting with `start-id` (optional).

  The resource handles are distinct and ordered by id."
  {:arglists
   '([batch-db compartment tid]
     [batch-db compartment tid start-id])}
  ([{:keys [snapshot] :as batch-db} compartment tid]
   (let [seek-key (encode-seek-key compartment tid)]
     (coll/eduction
      (resource-handle-xf batch-db tid)
      (i/prefix-keys snapshot :compartment-resource-type-index decode-key!
                     (bs/size seek-key) seek-key))))
  ([{:keys [snapshot] :as batch-db} compartment tid start-id]
   (coll/eduction
    (resource-handle-xf batch-db tid)
    (i/prefix-keys snapshot :compartment-resource-type-index decode-key!
                   (key-prefix-size (coll/nth compartment 1))
                   (encode-key compartment tid start-id)))))

(defn index-entry
  "Returns an entry of the CompartmentResourceType index build from `compartment`,
  `tid` and `id`."
  [compartment tid id]
  [:compartment-resource-type-index
   (bb/array (encode-key-buf compartment tid id))
   bytes/empty])
