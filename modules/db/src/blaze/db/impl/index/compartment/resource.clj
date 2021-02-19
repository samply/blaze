(ns blaze.db.impl.index.compartment.resource
  "Functions for accessing the CompartmentResource index."
  (:require
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.iterators :as i]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn- key-prefix-size ^long [^long co-res-id-size]
  (+ codec/c-hash-size co-res-id-size 1 codec/tid-size))


(def ^:private ^:const ^long max-key-size
  (+ codec/c-hash-size codec/max-id-size 1 codec/tid-size codec/max-id-size))


(defn- decode-key
  ([] (bb/allocate-direct max-key-size))
  ([buf]
   (bb/set-position! buf (+ (bb/position buf) codec/c-hash-size))
   (let [^long id-size (bb/size-up-to-null buf)]
     (bb/set-position! buf (+ (bb/position buf) id-size 1 codec/tid-size))
     (bs/from-byte-buffer buf))))


(defn- resource-handles-xform [resource-handle tid]
  (comp
    (keep #(resource-handle tid %))
    (remove (comp #{:delete} :op))))


(defn- encode-seek-key
  "Encodes the key without the id used for seeking to the start of scans."
  [[co-c-hash co-res-id] tid]
  (-> (bb/allocate (key-prefix-size (bs/size co-res-id)))
      (bb/put-int! co-c-hash)
      (bb/put-byte-string! co-res-id)
      (bb/put-byte! 0)
      (bb/put-int! tid)
      (bb/flip!)
      (bs/from-byte-buffer)))


(defn- encode-key-buf
  "Encodes the full key."
  [[co-c-hash co-res-id] tid id]
  (-> (bb/allocate (+ (key-prefix-size (bs/size co-res-id)) (bs/size id)))
      (bb/put-int! co-c-hash)
      (bb/put-byte-string! co-res-id)
      (bb/put-byte! 0)
      (bb/put-int! tid)
      (bb/put-byte-string! id)))


(defn- encode-key
  "Encodes the full key."
  [compartment tid id]
  (-> (encode-key-buf compartment tid id)
      (bb/flip!)
      (bs/from-byte-buffer)))


(defn resource-handles!
  "Returns a reducible collection of all resource handles of type with `tid`
  linked to `compartment`.

  An optional `start-id` can be given.

  Changes the state of `cri`. Consuming the collection requires exclusive
  access to `cri`. Doesn't close `cri`."
  ([{:keys [cri resource-handle]} compartment tid]
   (let [seek-key (encode-seek-key compartment tid)]
     (coll/eduction
       (resource-handles-xform resource-handle tid)
       (i/prefix-keys! cri seek-key decode-key seek-key))))
  ([{:keys [cri resource-handle]} compartment tid start-id]
   (coll/eduction
     (resource-handles-xform resource-handle tid)
     (i/prefix-keys!
       cri
       (encode-seek-key compartment tid)
       decode-key
       (encode-key compartment tid start-id)))))


(defn index-entry [compartment tid id]
  [:compartment-resource-type-index
   (bb/array (encode-key-buf compartment tid id))
   bytes/empty])
