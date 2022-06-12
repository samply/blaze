(ns blaze.db.impl.index.compartment.search-param-value-resource
  "Functions for accessing the CompartmentSearchParamValueResource index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.iterators :as i]
    [blaze.fhir.hash :as hash]))


(set! *unchecked-math* :warn-on-boxed)


(defn keys!
  "Returns a reducible collection of `[prefix did hash-prefix]` triples starting
  at `start-key`.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter start-key]
  (i/keys! iter sp-vr/decode-key start-key))


(defn- key-size ^long [value]
  (+ codec/c-hash-size codec/did-size
     codec/c-hash-size codec/tid-size (bs/size value)))


(defn encode-seek-key
  [[co-c-hash co-res-did] sp-c-hash tid value]
  (-> (bb/allocate (key-size value))
      (bb/put-int! co-c-hash)
      (bb/put-long! co-res-did)
      (bb/put-int! sp-c-hash)
      (bb/put-int! tid)
      (bb/put-byte-string! value)
      bb/flip!
      bs/from-byte-buffer!))


(defn prefix-keys!
  "Returns a reducible collection of `[did hash-prefix]` tuples starting at
  `value` and ending when `value` is no longer the prefix of the values
  processed.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter compartment c-hash tid value]
  (let [seek-key (encode-seek-key compartment c-hash tid value)]
    (i/prefix-keys! iter seek-key sp-vr/decode-did-hash-prefix seek-key)))


(defn- encode-key
  [[co-c-hash co-res-did] sp-c-hash tid value did hash]
  (-> (bb/allocate (+ (key-size value) 1 codec/did-size hash/prefix-size))
      (bb/put-int! co-c-hash)
      (bb/put-long! co-res-did)
      (bb/put-int! sp-c-hash)
      (bb/put-int! tid)
      (bb/put-byte-string! value)
      (bb/put-byte! 0)
      (bb/put-long! did)
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      bb/array))


(defn index-entry
  "Returns an entry of the CompartmentSearchParamValueResource index build from
  `compartment`, `c-hash`, `tid`, `value`, `did` and `hash`."
  [compartment c-hash tid value did hash]
  [:compartment-search-param-value-index
   (encode-key compartment c-hash tid value did hash)
   bytes/empty])
