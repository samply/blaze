(ns blaze.db.impl.index.compartment.search-param-value-resource
  "Functions for accessing the CompartmentSearchParamValueResource index."
  (:require
   [blaze.byte-string :as bs]
   [blaze.byte-string-builder :as bsb]
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
   (-> (bsb/allocate (key-size co-res-id value))
       (bsb/put-int! co-c-hash)
       (bsb/put-null-terminated-byte-string! co-res-id)
       (bsb/put-int! sp-c-hash)
       (bsb/put-int! tid)
       (bsb/put-byte-string! value)
       bsb/build))
  ([co-c-hash co-res-id sp-c-hash tid value prefix-length id]
   (-> (bsb/allocate (+ (long prefix-length) 2 (bs/size id)))
       (bsb/put-int! co-c-hash)
       (bsb/put-null-terminated-byte-string! co-res-id)
       (bsb/put-int! sp-c-hash)
       (bsb/put-int! tid)
       (bsb/put-null-terminated-byte-string! value)
       (bsb/put-byte-string! id)
       (bsb/put-byte! (bs/size id))
       bsb/build)))

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
    (-> (bsb/allocate (+ (key-size co-res-id value)
                         (bs/size id) 2 hash/prefix-size))
        (bsb/put-int! co-c-hash)
        (bsb/put-null-terminated-byte-string! co-res-id)
        (bsb/put-int! sp-c-hash)
        (bsb/put-int! tid)
        (bsb/put-null-terminated-byte-string! value)
        (bsb/put-byte-string! id)
        (bsb/put-byte! (bs/size id))
        (hash/prefix-into-byte-string-builder! hash)
        bsb/to-bytes)))

(defn index-entry
  "Returns an entry of the CompartmentSearchParamValueResource index build from
  `compartment`, `c-hash`, `tid`, `value`, `id` and `hash`."
  [compartment c-hash tid value id hash]
  [:compartment-search-param-value-index
   (encode-key compartment c-hash tid value id hash)
   bytes/empty])
