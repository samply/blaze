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
    [blaze.fhir.hash :as hash]))


(set! *unchecked-math* :warn-on-boxed)


(defn keys!
  "Returns a reducible collection of `[prefix id hash-prefix]` triples starting
  at `start-key`.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter start-key]
  (i/keys! iter sp-vr/decode-key start-key))


(defn- key-size ^long [co-res-id value]
  (+ codec/c-hash-size (bs/size co-res-id) 1
     codec/c-hash-size codec/tid-size (bs/size value)))


(defn encode-seek-key
  [compartment sp-c-hash tid value]
  (let [co-c-hash (coll/nth compartment 0)
        co-res-id (coll/nth compartment 1)]
    (-> (bb/allocate (key-size co-res-id value))
        (bb/put-int! co-c-hash)
        (bb/put-byte-string! co-res-id)
        (bb/put-byte! 0)
        (bb/put-int! sp-c-hash)
        (bb/put-int! tid)
        (bb/put-byte-string! value)
        bb/flip!
        bs/from-byte-buffer!)))


(defn prefix-keys!
  "Returns a reducible collection of `[id hash-prefix]` tuples starting at
  `value` and ending when `value` is no longer the prefix of the values
  processed.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter compartment c-hash tid value]
  (let [seek-key (encode-seek-key compartment c-hash tid value)]
    (i/prefix-keys! iter seek-key sp-vr/decode-id-hash-prefix seek-key)))


(defn- encode-key
  [compartment sp-c-hash tid value id hash]
  (let [co-c-hash (coll/nth compartment 0)
        co-res-id (coll/nth compartment 1)]
    (-> (bb/allocate (+ (key-size co-res-id value)
                        (bs/size id) 2 hash/prefix-size))
        (bb/put-int! co-c-hash)
        (bb/put-byte-string! co-res-id)
        (bb/put-byte! 0)
        (bb/put-int! sp-c-hash)
        (bb/put-int! tid)
        (bb/put-byte-string! value)
        (bb/put-byte! 0)
        (bb/put-byte-string! id)
        (bb/put-byte! (bs/size id))
        (hash/prefix-into-byte-buffer! (hash/prefix hash))
        bb/array)))


(defn index-entry
  "Returns an entry of the CompartmentSearchParamValueResource index build from
  `compartment`, `c-hash`, `tid`, `value`, `id` and `hash`."
  [compartment c-hash tid value id hash]
  [:compartment-search-param-value-index
   (encode-key compartment c-hash tid value id hash)
   bytes/empty])
