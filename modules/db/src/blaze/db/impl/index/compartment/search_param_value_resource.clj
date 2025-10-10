(ns blaze.db.impl.index.compartment.search-param-value-resource
  "Functions for accessing the CompartmentSearchParamValueResource index."
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

(defn- key-size
  ^long [co-res-id value]
  (+ codec/c-hash-size (bs/size co-res-id) 1
     codec/c-hash-size codec/tid-size (bs/size value)))

(defn- encode-seek-key
  ([co-c-hash co-res-id sp-c-hash tid value]
   (-> (bb/allocate (key-size co-res-id value))
       (bb/put-int! co-c-hash)
       (bb/put-null-terminated-byte-string! co-res-id)
       (bb/put-int! sp-c-hash)
       (bb/put-int! tid)
       (bb/put-byte-string! value)
       bb/flip!
       bs/from-byte-buffer!))
  ([co-c-hash co-res-id sp-c-hash tid value prefix-length id]
   (-> (bb/allocate (+ (long prefix-length) 2 (bs/size id)))
       (bb/put-int! co-c-hash)
       (bb/put-null-terminated-byte-string! co-res-id)
       (bb/put-int! sp-c-hash)
       (bb/put-int! tid)
       (bb/put-null-terminated-byte-string! value)
       (bb/put-byte-string! id)
       (bb/put-byte! (bs/size id))
       bb/flip!
       bs/from-byte-buffer!)))

(defn- index-handles* [snapshot prefix-length start-key]
  (coll/eduction
   u/by-id-grouper
   (i/prefix-keys
    snapshot
    :compartment-search-param-value-index
    sp-vr/decode-single-version-id
    prefix-length
    start-key)))

(defn index-handles
  "Returns a reducible collection of index handles from keys of `value`,
  starting with `start-id` (optional)."
  ([snapshot [co-c-hash co-res-id] c-hash tid value]
   (let [seek-key (encode-seek-key co-c-hash co-res-id c-hash tid value)]
     (index-handles* snapshot (bs/size seek-key) seek-key)))
  ([snapshot [co-c-hash co-res-id] c-hash tid value start-id]
   (let [prefix-length (key-size co-res-id value)
         seek-key (encode-seek-key co-c-hash co-res-id c-hash tid value
                                   prefix-length start-id)]
     (index-handles* snapshot prefix-length seek-key))))

(defn- encode-key
  [compartment sp-c-hash tid value id hash]
  (let [co-c-hash (coll/nth compartment 0)
        co-res-id (coll/nth compartment 1)]
    (-> (bb/allocate (+ (key-size co-res-id value)
                        (bs/size id) 2 hash/prefix-size))
        (bb/put-int! co-c-hash)
        (bb/put-null-terminated-byte-string! co-res-id)
        (bb/put-int! sp-c-hash)
        (bb/put-int! tid)
        (bb/put-null-terminated-byte-string! value)
        (bb/put-byte-string! id)
        (bb/put-byte! (bs/size id))
        (hash/prefix-into-byte-buffer! hash)
        bb/array)))

(defn index-entry
  "Returns an entry of the CompartmentSearchParamValueResource index build from
  `compartment`, `c-hash`, `tid`, `value`, `id` and `hash`."
  [compartment c-hash tid value id hash]
  [:compartment-search-param-value-index
   (encode-key compartment c-hash tid value id hash)
   bytes/empty])
