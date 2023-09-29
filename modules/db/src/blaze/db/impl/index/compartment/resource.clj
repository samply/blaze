(ns blaze.db.impl.index.compartment.resource
  "Functions for accessing the CompartmentResource index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.iterators :as i]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir.hash :as hash]))


(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long except-co-res-id-prefix-size
  (+ codec/c-hash-size 1 codec/tid-size))


(defn- key-prefix-size
  {:inline
   (fn [co-res-id]
     `(unchecked-add-int ~except-co-res-id-prefix-size (bs/size ~co-res-id)))}
  [co-res-id]
  (unchecked-add-int except-co-res-id-prefix-size (bs/size co-res-id)))


(defn- encode-seek-key
  ([compartment tid]
   (let [co-c-hash (coll/nth compartment 0)
         co-res-id (coll/nth compartment 1)]
     (-> (bb/allocate (key-prefix-size co-res-id))
         (bb/put-int! co-c-hash)
         (bb/put-byte-string! co-res-id)
         (bb/put-byte! 0)
         (bb/put-int! tid)
         bb/flip!
         bs/from-byte-buffer!)))
  ([compartment tid id]
   (let [co-c-hash (coll/nth compartment 0)
         co-res-id (coll/nth compartment 1)]
     (-> (bb/allocate (+ (key-prefix-size co-res-id) (bs/size id) 1))
         (bb/put-int! co-c-hash)
         (bb/put-byte-string! co-res-id)
         (bb/put-byte! 0)
         (bb/put-int! tid)
         (bb/put-byte-string! id)
         (bb/put-byte! (bs/size id))
         bb/flip!
         bs/from-byte-buffer!))))


(defn- encode-key [compartment tid id hash]
  (let [co-c-hash (coll/nth compartment 0)
        co-res-id (coll/nth compartment 1)]
    (-> (bb/allocate (+ (key-prefix-size co-res-id) (bs/size id) 1 hash/prefix-size))
        (bb/put-int! co-c-hash)
        (bb/put-byte-string! co-res-id)
        (bb/put-byte! 0)
        (bb/put-int! tid)
        (bb/put-byte-string! id)
        (bb/put-byte! (bs/size id))
        (hash/prefix-into-byte-buffer! (hash/prefix hash))
        bb/array)))


(defn resource-handles!
  "Returns a reducible collection of all resource handles of type with `tid`
  linked to `compartment`.

  An optional `start-id` can be given.

  Changes the state of `cri`. Consuming the collection requires exclusive
  access to `cri`. Doesn't close `cri`."
  {:arglists
   '([context compartment tid]
     [context compartment tid start-id])}
  ([{:keys [cri] :as context} compartment tid]
   (let [seek-key (encode-seek-key compartment tid)]
     (coll/eduction
       (u/resource-handle-mapper context tid)
       (i/prefix-keys! cri seek-key sp-vr/decode-id-hash-prefix seek-key))))
  ([{:keys [cri] :as context} compartment tid start-id]
   (coll/eduction
     (u/resource-handle-mapper context tid)
     (i/prefix-keys!
       cri
       (encode-seek-key compartment tid)
       sp-vr/decode-id-hash-prefix
       (encode-seek-key compartment tid start-id)))))


(defn index-entry
  "Returns an entry of the CompartmentResource index build from `compartment`,
  `tid`, `id` and `hash`."
  [compartment tid id hash]
  [:compartment-resource-type-index (encode-key compartment tid id hash)
   bytes/empty])
