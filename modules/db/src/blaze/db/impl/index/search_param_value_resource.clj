(ns blaze.db.impl.index.search-param-value-resource
  "Functions for accessing the SearchParamValueResource index."
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.iterators :as i]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long key-buffer-capacity
  "Most search param value keys should fit into this size."
  128)


(defn decode-key
  "Returns a triple of [prefix id hash-prefix]."
  ([] (bb/allocate-direct key-buffer-capacity))
  ([buf]
   (let [id-size (bb/get-byte! buf (dec (- (bb/limit buf) codec/hash-prefix-size)))
         all-size (bb/remaining buf)
         all (bs/from-byte-buffer buf)
         prefix-size (- all-size id-size 1 codec/hash-prefix-size)]
     [(bs/subs all 0 (dec prefix-size))
      (bs/subs all prefix-size (+ prefix-size id-size))
      (bs/subs all (- all-size codec/hash-prefix-size))])))


(defn keys!
  "Returns a reducible collection of `[prefix id hash-prefix]` triples starting
  at `start-key`.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter start-key]
  (i/keys! iter decode-key start-key))


(defn keys-prev!
  "Returns a reducible collection of `[prefix id hash-prefix]` triples starting
  at `start-key`, iterating in reverse.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter start-key]
  (i/keys-prev! iter decode-key start-key))


(defn- key-size
  (^long [value]
   (+ codec/c-hash-size codec/tid-size (bs/size value)))
  (^long [value id]
   (+ (key-size value) (bs/size id) 2)))


(defn encode-seek-key
  ([c-hash tid value]
   (-> (bb/allocate (key-size value))
       (bb/put-int! c-hash)
       (bb/put-int! tid)
       (bb/put-byte-string! value)
       (bb/flip!)
       (bs/from-byte-buffer)))
  ([c-hash tid value id]
   (-> (bb/allocate (key-size value id))
       (bb/put-int! c-hash)
       (bb/put-int! tid)
       (bb/put-byte-string! value)
       (bb/put-byte! 0)
       (bb/put-byte-string! id)
       (bb/put-byte! (bs/size id))
       (bb/flip!)
       (bs/from-byte-buffer))))


(defn encode-seek-key-for-prev
  ([c-hash tid value]
   (bs/concat (encode-seek-key c-hash tid value) (bs/from-hex "FF")))
  ([c-hash tid value id]
   (bs/concat (encode-seek-key c-hash tid value id) (bs/from-hex "FF"))))


(defn decode-id-hash-prefix
  "Returns a tuple of `[id hash-prefix]`."
  ([] (bb/allocate-direct key-buffer-capacity))
  ([buf]
   (let [limit (bb/limit buf)
         id-size (bb/get-byte! buf (dec (- limit codec/hash-prefix-size)))
         all-size (inc (+ id-size codec/hash-prefix-size))]
     (bb/set-position! buf (- limit all-size))
     [(bs/from-byte-buffer buf id-size)
      (do (bb/set-position! buf (inc (bb/position buf)))
          (bs/from-byte-buffer buf))])))


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


(defn- encode-key [c-hash tid value id hash]
  (-> (bb/allocate (+ (key-size value id) codec/hash-prefix-size))
      (bb/put-int! c-hash)
      (bb/put-int! tid)
      (bb/put-byte-string! value)
      (bb/put-byte! 0)
      (bb/put-byte-string! id)
      (bb/put-byte! (bs/size id))
      (bb/put-byte-string! (codec/hash-prefix hash))
      (bb/array)))


(defn index-entry [c-hash tid value id hash]
  [:search-param-value-index (encode-key c-hash tid value id hash) bytes/empty])
