(ns blaze.db.impl.iterators
  "This namespace provides a reducible collection abstraction over key-value
  store iterators.

  The reducible collections can be used together with transducers to obtain a
  collection of desired values that originated from a scan over a key-value
  store iterator, without having to deal with the low level and stateful API
  of the key-value store iterators.

  Direct byte buffers will be used to read keys and values via decode functions.
  Once decoded into immutable values, mutable byte buffers are no longer
  accessible to upstream transducers.

  All functions returning a reducible collection will change the state of and
  consuming them requires exclusive access to the key-value store iterator. So
  the API of this namespace produces still side effects. Upstream API's can be
  free of side effects by creating an exclusive key-value store iterator before
  calling one of the functions and closing it after the collection is consumed."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.kv :as kv])
  (:import
   [clojure.lang IReduceInit]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn- reduce-iter! [iter advance-fn rf init]
  (loop [ret init]
    (if (kv/valid? iter)
      (let [ret (rf ret iter)]
        (if (reduced? ret)
          @ret
          (do (advance-fn iter) (recur ret))))
      ret)))

(defn iter!
  "Returns a reducible collection of `iter` itself optionally starting at
  `start-key`.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([iter]
   (reify IReduceInit
     (reduce [_ rf init]
       (kv/seek-to-first! iter)
       (reduce-iter! iter kv/next! rf init))))
  ([iter start-key]
   (reify IReduceInit
     (reduce [_ rf init]
       (kv/seek! iter (bs/to-byte-array start-key))
       (reduce-iter! iter kv/next! rf init)))))

(defn iter-prev!
  "Returns a reducible collection of `iter` itself, iterating in reverse.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter start-key]
  (reify IReduceInit
    (reduce [_ rf init]
      (kv/seek-for-prev! iter (bs/to-byte-array start-key))
      (reduce-iter! iter kv/prev! rf init))))

(def key-reader
  "Returns a transducer that will read the key from a downstream iterator."
  (map (comp bb/wrap kv/key)))

(defn- key-decoder [decode]
  (comp
   key-reader
   (map decode)))

(defn keys!
  "Returns a reducible collection of decoded keys of `iter` starting with
  `start-key`.

  The `decode` function has to return a direct ByteBuffer when called with no
  argument. That same ByteBuffer will be used for each key read and will be
  passed to the decode function for decoding into a value which will end up in
  the collection.

  If the capacity of the ByteBuffer isn't sufficient, a new ByteBuffer with
  double the capacity will be created and used for further reads.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter decode start-key]
  (coll/eduction
   (key-decoder decode)
   (iter! iter start-key)))

(defn- take-while-prefix-matches
  "Returns a transducer that takes key buffers as long as their contents start
  with the bytes from `prefix`."
  [prefix]
  (let [prefix-size (bs/size prefix)
        prefix-buf (bb/allocate prefix-size)]
    (bb/put-byte-string! prefix-buf prefix)
    (bb/flip! prefix-buf)
    (take-while
     (fn [buf]
       (let [mismatch (bb/mismatch buf prefix-buf)]
         (or (<= prefix-size mismatch) (neg? mismatch)))))))

(defn- prefix-xf [prefix decode]
  (comp
   key-reader
   (comp
    (take-while-prefix-matches prefix)
    (map decode))))

(defn prefix-keys!
  "Returns a reducible collection of decoded keys of `iter` starting with
  `start-key` and ending when `prefix` no longer matches.

  The `decode` function has to return a direct ByteBuffer when called with no
  argument. That same ByteBuffer will be used for each key read and will be
  passed to the decode function for decoding into a value which will end up in
  the collection.

  If the capacity of the ByteBuffer isn't sufficient, a new ByteBuffer with
  double the capacity will be created and used for further reads.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter prefix decode start-key]
  (coll/eduction (prefix-xf prefix decode) (iter! iter start-key)))

(defn prefix-keys-prev!
  "Returns a reducible collection of decoded keys of `iter` starting with
  `start-key` and ending when `prefix` no longer matches, iterating in reverse.

  The `decode` function has to return a direct ByteBuffer when called with no
  argument. That same ByteBuffer will be used for each key read and will be
  passed to the decode function for decoding into a value which will end up in
  the collection.

  If the capacity of the ByteBuffer isn't sufficient, a new ByteBuffer with
  double the capacity will be created and used for further reads.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter prefix decode start-key]
  (coll/eduction (prefix-xf prefix decode) (iter-prev! iter start-key)))

(defn- kv-decoder [decode]
  (map #(decode (bb/wrap (kv/key %)) (bb/wrap (kv/value %)))))

(defn kvs!
  "Returns a reducible collection of decoded keys and values of `iter`.

  When called with no argument, the `decode` function has to return a tuple of
  direct ByteBuffers, the first for the key and the second for the value. The
  ByteBuffer will be used for each key and value read and will be passed to the
  decode function for decoding into a value which will end up in the collection.

  If the capacity of either of the ByteBuffers isn't sufficient, a new
  ByteBuffer with double the capacity will be created and used for further
  reads.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  [iter decode start-key]
  (coll/eduction
   (kv-decoder decode)
   (iter! iter start-key)))
