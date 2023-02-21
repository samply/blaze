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


(def ^:private ^:const ^long seek-key-size
  (+ codec/c-hash-size codec/did-size codec/tid-size))


(def ^:private ^:const ^long key-size
  (+ seek-key-size codec/did-size))


(defn- decode-key
  ([] (bb/allocate-direct key-size))
  ([buf]
   (bb/set-position! buf seek-key-size)
   (bb/get-long! buf)))


(def ^:private remove-deleted-xf
  (remove rh/deleted?))


(defn- resource-handles-xf [resource-handle tid]
  (comp
    (keep #(resource-handle tid %))
    remove-deleted-xf))


(defn- encode-seek-key
  "Encodes the key without the id used for seeking to the start of scans."
  [[co-c-hash co-res-did] tid]
  (-> (bb/allocate seek-key-size)
      (bb/put-int! co-c-hash)
      (bb/put-long! co-res-did)
      (bb/put-int! tid)
      bb/flip!
      bs/from-byte-buffer!))


(defn- encode-key-buf
  "Encodes the full key."
  [[co-c-hash co-res-did] tid did]
  (-> (bb/allocate key-size)
      (bb/put-int! co-c-hash)
      (bb/put-long! co-res-did)
      (bb/put-int! tid)
      (bb/put-long! did)))


(defn- encode-key
  "Encodes the full key."
  [compartment tid id]
  (-> (encode-key-buf compartment tid id) bb/flip! bs/from-byte-buffer!))


(defn resource-handles!
  "Returns a reducible collection of all resource handles of type with `tid`
  linked to `compartment`.

  An optional `start-did` can be given.

  Changes the state of `cri`. Consuming the collection requires exclusive
  access to `cri`. Doesn't close `cri`."
  {:arglists
   '([context compartment tid]
     [context compartment tid start-did])}
  ([{:keys [cri resource-handle]} compartment tid]
   (let [seek-key (encode-seek-key compartment tid)]
     (coll/eduction
       (resource-handles-xf resource-handle tid)
       (i/prefix-keys! cri seek-key decode-key seek-key))))
  ([{:keys [cri resource-handle]} compartment tid start-did]
   (coll/eduction
     (resource-handles-xf resource-handle tid)
     (i/prefix-keys!
       cri
       (encode-seek-key compartment tid)
       decode-key
       (encode-key compartment tid start-did)))))


(defn index-entry
  "Returns an entry of the CompartmentResource index build from `compartment`,
  `tid` and `did`."
  [compartment tid did]
  [:compartment-resource-type-index
   (bb/array (encode-key-buf compartment tid did))
   bytes/empty])
