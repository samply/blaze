(ns blaze.db.impl.index.compartment.search-param-value-resource
  "Functions for accessing the CompartmentSearchParamValueResource index."
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.iterators :as i]))


(set! *warn-on-reflection* true)
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


(defn encode-seek-key [[co-c-hash co-res-id] sp-c-hash tid value]
  (-> (bb/allocate (key-size co-res-id value))
      (bb/put-int! co-c-hash)
      (bb/put-byte-string! co-res-id)
      (bb/put-byte! 0)
      (bb/put-int! sp-c-hash)
      (bb/put-int! tid)
      (bb/put-byte-string! value)
      bb/flip!
      bs/from-byte-buffer))


(defn prefix-keys!
  "Returns a reducible collection of `[id hash-prefix]` tuples starting at
  `start-value` and ending when `prefix-value` is no longer the prefix
  of the values processed.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter compartment c-hash tid prefix-value start-value]
  (i/prefix-keys!
    iter
    (encode-seek-key compartment c-hash tid prefix-value)
    sp-vr/decode-id-hash-prefix
    (encode-seek-key compartment c-hash tid start-value)))


(defn- encode-key [[co-c-hash co-res-id] sp-c-hash tid value id hash]
  (-> (bb/allocate (+ (key-size co-res-id value)
                      (bs/size id) 2 codec/hash-prefix-size))
      (bb/put-int! co-c-hash)
      (bb/put-byte-string! co-res-id)
      (bb/put-byte! 0)
      (bb/put-int! sp-c-hash)
      (bb/put-int! tid)
      (bb/put-byte-string! value)
      (bb/put-byte! 0)
      (bb/put-byte-string! id)
      (bb/put-byte! (bs/size id))
      (bb/put-byte-string! (codec/hash-prefix hash))
      bb/array))


(defn index-entry
  "Returns an entry of the CompartmentSearchParamValueResource index build from
  `compartment`, `c-hash`, `tid`, `value`, `id` and `hash`."
  [compartment c-hash tid value id hash]
  [:compartment-search-param-value-index
   (encode-key compartment c-hash tid value id hash)
   bytes/empty])
