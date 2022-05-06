(ns blaze.db.impl.index.search-param-value-resource
  "Functions for accessing the SearchParamValueResource index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.search-param-value-resource.impl :as impl]
    [blaze.db.impl.iterators :as i]
    [blaze.fhir.hash :as hash]))


(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long key-buffer-capacity
  "Most search param value keys should fit into this size."
  128)


(defn decode-key
  "Returns a triple of `[prefix id hash-prefix]`.

  The prefix contains the c-hash, tid and value parts as encoded byte string."
  ([] (bb/allocate-direct key-buffer-capacity))
  ([buf]
   (let [id-size (impl/id-size buf)
         all-size (bb/remaining buf)
         prefix-size (- all-size 2 id-size hash/prefix-size)
         prefix (bs/from-byte-buffer! buf prefix-size)
         _ (bb/get-byte! buf)
         id (bs/from-byte-buffer! buf id-size)]
     (bb/get-byte! buf)
     [prefix id (hash/prefix-from-byte-buffer! buf)])))


(defn keys!
  "Returns a reducible collection of `[prefix id hash-prefix]` triples starting
  at `start-key`.

  The prefix contains the c-hash, tid and value parts as encoded byte string.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter start-key]
  (i/keys! iter decode-key start-key))


(def ^:const ^long base-key-size
  (+ codec/c-hash-size codec/tid-size))


(defn- key-size
  (^long [value]
   (+ base-key-size (bs/size value)))
  (^long [value id]
   (+ (key-size value) (bs/size id) 2)))


(defn encode-seek-key
  ([c-hash tid]
   (-> (bb/allocate base-key-size)
       (bb/put-int! c-hash)
       (bb/put-int! tid)
       bb/flip!
       bs/from-byte-buffer!))
  ([c-hash tid value]
   (-> (bb/allocate (key-size value))
       (bb/put-int! c-hash)
       (bb/put-int! tid)
       (bb/put-byte-string! value)
       bb/flip!
       bs/from-byte-buffer!))
  ([c-hash tid value id]
   (-> (bb/allocate (key-size value id))
       (bb/put-int! c-hash)
       (bb/put-int! tid)
       (bb/put-byte-string! value)
       (bb/put-byte! 0)
       (bb/put-byte-string! id)
       (bb/put-byte! (bs/size id))
       bb/flip!
       bs/from-byte-buffer!)))


(def ^:private bs-ff
  (bs/from-hex "FF"))


(defn encode-seek-key-for-prev
  ([c-hash tid value]
   (bs/concat (encode-seek-key c-hash tid value) bs-ff))
  ([c-hash tid value id]
   (bs/concat (encode-seek-key c-hash tid value id) bs-ff)))


(defn decode-value-id-hash-prefix
  "Returns a triple of `[value id hash-prefix]`."
  ([] (bb/allocate-direct key-buffer-capacity))
  ([buf]
   (let [id-size (impl/id-size buf)
         _ (bb/set-position! buf base-key-size)
         all-size (bb/remaining buf)
         value-size (- all-size 2 id-size hash/prefix-size)
         value (bs/from-byte-buffer! buf value-size)
         _ (bb/get-byte! buf)
         id (bs/from-byte-buffer! buf id-size)]
     (bb/get-byte! buf)
     [value id (hash/prefix-from-byte-buffer! buf)])))


(defn all-keys!
  "Returns a reducible collection of `[value id hash-prefix]` triples of the
  whole range prefixed with `c-hash` and `tid` starting with `start-value` and
  `start-id` (optional).

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([iter c-hash tid]
   (let [prefix (encode-seek-key c-hash tid)]
     (i/prefix-keys! iter prefix decode-value-id-hash-prefix prefix)))
  ([iter c-hash tid start-value start-id]
   (let [prefix (encode-seek-key c-hash tid)
         start-key (encode-seek-key c-hash tid start-value start-id)]
     (i/prefix-keys! iter prefix decode-value-id-hash-prefix start-key))))


(defn decode-id-hash-prefix
  "Returns a tuple of `[id hash-prefix]`."
  ([] (bb/allocate-direct key-buffer-capacity))
  ([buf]
   (let [id-size (impl/id-size buf)
         all-size (unchecked-inc-int (unchecked-add-int id-size hash/prefix-size))
         _ (bb/set-position! buf (unchecked-subtract-int (bb/limit buf) all-size))
         id (bs/from-byte-buffer! buf id-size)]
     (bb/get-byte! buf)
     [id (hash/prefix-from-byte-buffer! buf)])))


(defn prefix-keys!
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `start-value` and optional `start-id` and ending when
  `prefix-value` is no longer a prefix of the values processed.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([iter c-hash tid prefix-value start-value]
   (i/prefix-keys!
     iter (encode-seek-key c-hash tid prefix-value) decode-id-hash-prefix
     (encode-seek-key c-hash tid start-value)))
  ([iter c-hash tid prefix-value start-value start-id]
   (i/prefix-keys!
     iter (encode-seek-key c-hash tid prefix-value) decode-id-hash-prefix
     (encode-seek-key c-hash tid start-value start-id))))


(defn prefix-keys'!
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `start-value` and optional `start-id` and ending when
  `prefix-value` is no longer a prefix of the values processed.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter c-hash tid prefix-value start-value]
  (i/prefix-keys!
    iter (encode-seek-key c-hash tid prefix-value) decode-id-hash-prefix
    (encode-seek-key-for-prev c-hash tid start-value)))


(defn prefix-keys-prev!
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `start-value` and optional `start-id` and ending when
  `prefix-value` is no longer a prefix of the values processed, iterating in
  reverse.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([iter c-hash tid prefix-value start-value]
   (i/prefix-keys-prev!
     iter (encode-seek-key c-hash tid prefix-value) decode-id-hash-prefix
     (encode-seek-key-for-prev c-hash tid start-value)))
  ([iter c-hash tid prefix-value start-value start-id]
   (i/prefix-keys-prev!
     iter (encode-seek-key c-hash tid prefix-value) decode-id-hash-prefix
     (encode-seek-key-for-prev c-hash tid start-value start-id))))


(defn prefix-keys-prev'!
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `start-value` and optional `start-id` and ending when
  `prefix-value` is no longer a prefix of the values processed, iterating in
  reverse.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter c-hash tid prefix-value start-value]
  (i/prefix-keys-prev!
    iter (encode-seek-key c-hash tid prefix-value) decode-id-hash-prefix
    (encode-seek-key c-hash tid start-value)))


(defn encode-key [c-hash tid value id hash]
  (-> (bb/allocate (unchecked-add-int (key-size value id) hash/prefix-size))
      (bb/put-int! c-hash)
      (bb/put-int! tid)
      (bb/put-byte-string! value)
      (bb/put-byte! 0)
      (bb/put-byte-string! id)
      (bb/put-byte! (bs/size id))
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      bb/array))


(defn index-entry
  "Returns an entry of the SearchParamValueResource index build from `c-hash`,
  `tid`, `value`, `id` and `hash`."
  [c-hash tid value id hash]
  [:search-param-value-index (encode-key c-hash tid value id hash) bytes/empty])
