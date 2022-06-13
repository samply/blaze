(ns blaze.db.impl.index.search-param-value-resource
  "Functions for accessing the SearchParamValueResource index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.iterators :as i]
    [blaze.fhir.hash :as hash]))


(set! *unchecked-math* :warn-on-boxed)


(def ^:const ^long key-buffer-capacity
  "Most search param value keys should fit into this size."
  64)


(defn decode-key
  "Returns a triple of `[prefix did hash-prefix]`.

  The prefix contains the c-hash, tid and value parts as encoded byte string."
  ([] (bb/allocate-direct key-buffer-capacity))
  ([buf]
   (let [all-size (bb/remaining buf)
         prefix-size (- all-size 1 codec/did-size hash/prefix-size)
         prefix (bs/from-byte-buffer! buf prefix-size)
         _ (bb/get-byte! buf)
         did (bb/get-long! buf)]
     [prefix did (hash/prefix-from-byte-buffer! buf)])))


(defn keys!
  "Returns a reducible collection of `[prefix did hash-prefix]` triples starting
  at `start-key`.

  The prefix contains the c-hash, tid and value parts as encoded byte string.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter start-key]
  (i/keys! iter decode-key start-key))


(def ^:const ^long base-key-size
  (+ codec/c-hash-size codec/tid-size))


(defn encode-seek-key
  ([c-hash tid]
   (-> (bb/allocate base-key-size)
       (bb/put-int! c-hash)
       (bb/put-int! tid)
       bb/flip!
       bs/from-byte-buffer!))
  ([c-hash tid value]
   (-> (bb/allocate (+ base-key-size (bs/size value)))
       (bb/put-int! c-hash)
       (bb/put-int! tid)
       (bb/put-byte-string! value)
       bb/flip!
       bs/from-byte-buffer!))
  ([c-hash tid value did]
   (-> (bb/allocate (+ base-key-size (bs/size value) 1 codec/did-size))
       (bb/put-int! c-hash)
       (bb/put-int! tid)
       (bb/put-byte-string! value)
       (bb/put-byte! 0)
       (bb/put-long! did)
       bb/flip!
       bs/from-byte-buffer!)))


(def ^:private bs-ff
  (bs/from-hex "FF"))


(defn encode-seek-key-for-prev
  ([c-hash tid value]
   (bs/concat (encode-seek-key c-hash tid value) bs-ff))
  ([c-hash tid value did]
   (bs/concat (encode-seek-key c-hash tid value did) bs-ff)))


(defn decode-value-did-hash-prefix
  "Returns a triple of `[value did hash-prefix]`."
  ([] (bb/allocate-direct key-buffer-capacity))
  ([buf]
   (let [_ (bb/set-position! buf base-key-size)
         remaining-size (bb/remaining buf)
         value-size (- remaining-size 1 codec/did-size hash/prefix-size)
         value (bs/from-byte-buffer! buf value-size)
         _ (bb/get-byte! buf)
         did (bb/get-long! buf)]
     [value did (hash/prefix-from-byte-buffer! buf)])))


(defn all-keys!
  "Returns a reducible collection of `[value did hash-prefix]` triples of the
  whole range prefixed with `c-hash` and `tid` starting with `start-value` and
  `start-did` (optional).

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([iter c-hash tid]
   (let [prefix (encode-seek-key c-hash tid)]
     (i/prefix-keys! iter prefix decode-value-did-hash-prefix prefix)))
  ([iter c-hash tid start-value start-did]
   (let [prefix (encode-seek-key c-hash tid)
         start-key (encode-seek-key c-hash tid start-value start-did)]
     (i/prefix-keys! iter prefix decode-value-did-hash-prefix start-key))))


(defn decode-did-hash-prefix
  "Returns a tuple of `[did hash-prefix]`."
  ([] (bb/allocate-direct key-buffer-capacity))
  ([buf]
   (bb/set-position! buf (- (bb/limit buf) codec/did-size hash/prefix-size))
   [(bb/get-long! buf) (hash/prefix-from-byte-buffer! buf)]))


(defn prefix-keys!
  "Returns a reducible collection of decoded `[did hash-prefix]` tuples from keys
  starting at `start-value` and optional `start-did` and ending when
  `prefix-value` is no longer a prefix of the values processed.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([iter c-hash tid prefix-value start-value]
   (i/prefix-keys!
     iter (encode-seek-key c-hash tid prefix-value) decode-did-hash-prefix
     (encode-seek-key c-hash tid start-value)))
  ([iter c-hash tid prefix-value start-value start-did]
   (i/prefix-keys!
     iter (encode-seek-key c-hash tid prefix-value) decode-did-hash-prefix
     (encode-seek-key c-hash tid start-value start-did))))


(defn prefix-keys'!
  "Returns a reducible collection of decoded `[did hash-prefix]` tuples from keys
  starting at `start-value` and optional `start-did` and ending when
  `prefix-value` is no longer a prefix of the values processed.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter c-hash tid prefix-value start-value]
  (i/prefix-keys!
    iter (encode-seek-key c-hash tid prefix-value) decode-did-hash-prefix
    (encode-seek-key-for-prev c-hash tid start-value)))


(defn prefix-keys-prev!
  "Returns a reducible collection of decoded `[did hash-prefix]` tuples from keys
  starting at `start-value` and optional `start-did` and ending when
  `prefix-value` is no longer a prefix of the values processed, iterating in
  reverse.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([iter c-hash tid prefix-value start-value]
   (i/prefix-keys-prev!
     iter (encode-seek-key c-hash tid prefix-value) decode-did-hash-prefix
     (encode-seek-key-for-prev c-hash tid start-value)))
  ([iter c-hash tid prefix-value start-value start-did]
   (i/prefix-keys-prev!
     iter (encode-seek-key c-hash tid prefix-value) decode-did-hash-prefix
     (encode-seek-key-for-prev c-hash tid start-value start-did))))


(defn prefix-keys-prev'!
  "Returns a reducible collection of decoded `[did hash-prefix]` tuples from keys
  starting at `start-value` and optional `start-did` and ending when
  `prefix-value` is no longer a prefix of the values processed, iterating in
  reverse.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter c-hash tid prefix-value start-value]
  (i/prefix-keys-prev!
    iter (encode-seek-key c-hash tid prefix-value) decode-did-hash-prefix
    (encode-seek-key c-hash tid start-value)))


(defn encode-key [c-hash tid value did hash]
  (-> (bb/allocate (+ base-key-size (bs/size value) 1 codec/did-size hash/prefix-size))
      (bb/put-int! c-hash)
      (bb/put-int! tid)
      (bb/put-byte-string! value)
      (bb/put-byte! 0)
      (bb/put-long! did)
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      bb/array))


(defn index-entry
  "Returns an entry of the SearchParamValueResource index build from `c-hash`,
  `tid`, `value`, `did` and `hash`."
  [c-hash tid value did hash]
  [:search-param-value-index (encode-key c-hash tid value did hash) bytes/empty])
