(ns blaze.db.impl.index.compartment.resource
  "Functions for accessing the CompartmentResource index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i]))


(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long max-key-size
  (+ codec/c-hash-size codec/max-id-size 1 codec/tid-size codec/max-id-size))


(def ^:private ^:const ^long except-co-res-id-prefix-size
  (+ codec/c-hash-size 1 codec/tid-size))


(defn- key-prefix-size
  {:inline
   (fn [co-res-id]
     `(unchecked-add-int ~except-co-res-id-prefix-size (bs/size ~co-res-id)))}
  [co-res-id]
  (unchecked-add-int except-co-res-id-prefix-size (bs/size co-res-id)))


(defn- decode-key
  ([] (bb/allocate-direct max-key-size))
  ([buf]
   (bb/set-position! buf (unchecked-add-int (bb/position buf) codec/c-hash-size))
   (let [id-size (long (bb/size-up-to-null buf))]
     (bb/set-position! buf (+ (bb/position buf) id-size 1 codec/tid-size))
     (bs/from-byte-buffer buf))))


(def ^:private remove-deleted-xf
  (remove rh/deleted?))


(defn- resource-handles-xf [resource-handle tid]
  (comp
    (keep #(resource-handle tid %))
    remove-deleted-xf))


(defn- encode-seek-key
  "Encodes the key without the id used for seeking to the start of scans."
  [compartment tid]
  (let [co-c-hash (coll/nth compartment 0)
        co-res-id (coll/nth compartment 1)]
    (-> (bb/allocate (key-prefix-size co-res-id))
        (bb/put-int! co-c-hash)
        (bb/put-byte-string! co-res-id)
        (bb/put-byte! 0)
        (bb/put-int! tid)
        bb/flip!
        bs/from-byte-buffer)))


(defn- encode-key-buf
  "Encodes the full key."
  [compartment tid id]
  (let [co-c-hash (coll/nth compartment 0)
        co-res-id (coll/nth compartment 1)]
    (-> (bb/allocate (unchecked-add-int (key-prefix-size co-res-id) (bs/size id)))
        (bb/put-int! co-c-hash)
        (bb/put-byte-string! co-res-id)
        (bb/put-byte! 0)
        (bb/put-int! tid)
        (bb/put-byte-string! id))))


(defn- encode-key
  "Encodes the full key."
  [compartment tid id]
  (-> (encode-key-buf compartment tid id) bb/flip! bs/from-byte-buffer))


(defn resource-handles!
  "Returns a reducible collection of all resource handles of type with `tid`
  linked to `compartment`.

  An optional `start-id` can be given.

  Changes the state of `cri`. Consuming the collection requires exclusive
  access to `cri`. Doesn't close `cri`."
  {:arglists
   '([context compartment tid]
     [context compartment tid start-id])}
  ([{:keys [cri resource-handle]} compartment tid]
   (let [seek-key (encode-seek-key compartment tid)]
     (coll/eduction
       (resource-handles-xf resource-handle tid)
       (i/prefix-keys! cri seek-key decode-key seek-key))))
  ([{:keys [cri resource-handle]} compartment tid start-id]
   (coll/eduction
     (resource-handles-xf resource-handle tid)
     (i/prefix-keys!
       cri
       (encode-seek-key compartment tid)
       decode-key
       (encode-key compartment tid start-id)))))


(defn index-entry
  "Returns an entry of the CompartmentResource index build from `compartment`,
  `tid` and `id`."
  [compartment tid id]
  [:compartment-resource-type-index
   (bb/array (encode-key-buf compartment tid id))
   bytes/empty])
