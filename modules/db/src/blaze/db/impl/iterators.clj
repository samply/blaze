(ns blaze.db.impl.iterators
  "This namespace provides a reducible collection abstraction over key-value
  store iterators.

  The reducible collections can be used together with transducers to obtain a
  collection of desired values that originated from a scan over a key-value
  store iterator, without having to deal with the low level and stateful API
  of the key-value store iterators."
  (:refer-clojure :exclude [key keys])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.kv :as kv])
  (:import
   [clojure.lang IReduceInit]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn contains-key-prefix?
  "Returns true iff a key with `key-prefix` exists in `column-family`."
  [snapshot column-family key-prefix]
  (with-open [iter (kv/new-iterator snapshot column-family)]
    (let [target (bs/to-byte-array key-prefix)]
      (kv/seek! iter target)
      (when (kv/valid? iter)
        (let [target-buf (bb/wrap target)
              key-buf (bb/allocate (bs/size key-prefix))]
          (kv/key! iter key-buf)
          (= key-buf target-buf))))))

(defn- prefix-matches? [target prefix-length buf]
  (let [prefix-buf (bb/allocate prefix-length)]
    (bb/put-byte-string! prefix-buf (bs/subs target 0 prefix-length))
    (bb/flip! prefix-buf)
    (let [mismatch (bb/mismatch buf prefix-buf)]
      (or (<= (long prefix-length) mismatch) (neg? mismatch)))))

(defn seek-key
  "Returns the first decoded key of `column-family` were the key is at or past
  `target` with at least `prefix-length` bytes matching.

  The `decode` function has to accept a ByteBuffer and decode it into a value
  which will be returned."
  [snapshot column-family decode prefix-length target]
  (with-open [iter (kv/new-iterator snapshot column-family)]
    (kv/seek! iter (bs/to-byte-array target))
    (when (kv/valid? iter)
      (let [key-buf (bb/wrap (kv/key iter))]
        (when (prefix-matches? target prefix-length key-buf)
          (decode key-buf))))))

(defn seek-key-first
  "Returns the first decoded key of `column-family`.

  The `decode` function has to accept a ByteBuffer and decode it into a value
  which will be returned."
  [snapshot column-family decode]
  (with-open [iter (kv/new-iterator snapshot column-family)]
    (kv/seek-to-first! iter)
    (when (kv/valid? iter)
      (decode (bb/wrap (kv/key iter))))))

(defn seek-key-prev
  "Returns the first decoded key of `column-family` were the key is at or before
  `target` with at least `prefix-length` bytes matching.

  The `decode` function has to accept a ByteBuffer and decode it into a value
  which will be returned."
  [snapshot column-family decode prefix-length target]
  (with-open [iter (kv/new-iterator snapshot column-family)]
    (kv/seek-for-prev! iter (bs/to-byte-array target))
    (when (kv/valid? iter)
      (let [key-buf (bb/wrap (kv/key iter))]
        (when (prefix-matches? target prefix-length key-buf)
          (decode key-buf))))))

(defn seek-value
  "Returns the first decoded value of `column-family` were the key is at or past
  `target` with at least `prefix-length` bytes matching.

  The `decode` function has to accept a ByteBuffer and decode it into a value
  which will be returned."
  [snapshot column-family decode prefix-length target]
  (with-open [iter (kv/new-iterator snapshot column-family)]
    (kv/seek! iter (bs/to-byte-array target))
    (when (kv/valid? iter)
      (let [key-buf (bb/wrap (kv/key iter))]
        (when (prefix-matches? target prefix-length key-buf)
          (decode (bb/wrap (kv/value iter))))))))

(defn- reduce-iter! [iter advance-fn rf init]
  (loop [ret init]
    (if (kv/valid? iter)
      (let [ret (rf ret iter)]
        (if (reduced? ret)
          @ret
          (do (advance-fn iter) (recur ret))))
      ret)))

(defn- coll
  ([snapshot column-family xf]
   (reify IReduceInit
     (reduce [_ rf init]
       (with-open [iter (kv/new-iterator snapshot column-family)]
         (kv/seek-to-first! iter)
         (reduce-iter! iter kv/next! (xf rf) init)))))
  ([snapshot column-family xf start-key]
   (reify IReduceInit
     (reduce [_ rf init]
       (with-open [iter (kv/new-iterator snapshot column-family)]
         (kv/seek! iter (bs/to-byte-array start-key))
         (reduce-iter! iter kv/next! (xf rf) init))))))

(defn- coll-prev [snapshot column-family xf start-key]
  (reify IReduceInit
    (reduce [_ rf init]
      (with-open [iter (kv/new-iterator snapshot column-family)]
        (kv/seek-for-prev! iter (bs/to-byte-array start-key))
        (reduce-iter! iter kv/prev! (xf rf) init)))))

(def ^:private key-reader
  "Returns a transducer that will read the key from a downstream iterator."
  (map (comp bb/wrap kv/key)))

(defn- key-decoder [decode]
  (comp
   key-reader
   (map decode)))

(defn keys
  "Returns a reducible collection of decoded keys of `column-family` starting
  with `start-key`.

  The `decode` function has to accept a ByteBuffer and decode it into a value
  which will end up in the collection."
  [snapshot column-family decode start-key]
  (coll snapshot column-family (key-decoder decode) start-key))

(defn- take-while-prefix-matches
  "Returns a transducer that takes key buffers as long as their contents start
  with the bytes from `prefix`."
  [start-key prefix-length]
  (let [prefix-buf (bb/allocate prefix-length)]
    (bb/put-byte-string! prefix-buf (bs/subs start-key 0 prefix-length))
    (bb/flip! prefix-buf)
    (take-while
     (fn [buf]
       (let [mismatch (bb/mismatch buf prefix-buf)]
         (or (<= (long prefix-length) mismatch) (neg? mismatch)))))))

(defn- prefix-xf [start-key prefix-length decode]
  (comp
   key-reader
   (comp
    (take-while-prefix-matches start-key prefix-length)
    (map decode))))

(defn prefix-keys
  "Returns a reducible collection of decoded keys of `column-family` starting
  with `start-key` and ending when `prefix` no longer matches.

  The `decode` function has to accept a ByteBuffer and decode it into a value
  which will end up in the collection."
  [snapshot column-family decode prefix-length start-key]
  (coll snapshot column-family (prefix-xf start-key prefix-length decode)
        start-key))

(defn prefix-keys-prev
  "Returns a reducible collection of decoded keys of `column-family` starting
  with `start-key` and ending when `prefix` no longer matches, iterating in
  reverse.

  The `decode` function has to accept a ByteBuffer and decode it into a value
  which will end up in the collection."
  [snapshot column-family decode prefix-length start-key]
  (coll-prev snapshot column-family (prefix-xf start-key prefix-length decode)
             start-key))

(defprotocol Entry
  (-key [_])
  (-value [_]))

(defn key
  "Returns the key of `entry` that is returned by the `entries` function."
  [entry]
  (-key entry))

(defn value
  "Returns the value of `entry` that is returned by the `entries` function."
  [entry]
  (-value entry))

(deftype IterEntry [iter]
  Entry
  (-key [_]
    (bb/wrap (kv/key iter)))
  (-value [_]
    (bb/wrap (kv/value iter))))

(defn entries
  "Returns a reducible collection of entries of `column-family` optionally
  starting at `start-key`.

  The key and value of each entry can be obtained by calling the `key` and
  `value` function."
  ([snapshot column-family]
   (coll snapshot column-family (map ->IterEntry)))
  ([snapshot column-family start-key]
   (coll snapshot column-family (map ->IterEntry) start-key)))
