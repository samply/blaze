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
   [blaze.coll.core :as coll]
   [blaze.db.kv :as kv])
  (:import
   [clojure.lang Counted IReduceInit]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:const ^long buffer-size 128)

(defn- prefix-matches* [buf-1 buf-2 length]
  (let [mismatch (bb/mismatch buf-1 buf-2)]
    (or (<= (long length) mismatch) (neg? mismatch))))

(defn- prefix-matches? [target prefix-length buf]
  (let [prefix-buf (bb/allocate prefix-length)]
    (bb/put-byte-string! prefix-buf (bs/subs target 0 prefix-length))
    (bb/flip! prefix-buf)
    (prefix-matches* buf prefix-buf prefix-length)))

(defn seek-key
  "Returns the first decoded key of `column-family` were the key is at or past
  `target` with at least `prefix-length` bytes matching.

  The `decode` function has to accept a byte buffer and decode it into a value
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

  The `decode` function has to accept a byte buffer and decode it into a value
  which will be returned."
  [snapshot column-family decode]
  (with-open [iter (kv/new-iterator snapshot column-family)]
    (kv/seek-to-first! iter)
    (when (kv/valid? iter)
      (decode (bb/wrap (kv/key iter))))))

(defn seek-value
  "Returns the first decoded value of `column-family` were the key is at or past
  `target` with at least `prefix-length` bytes matching.

  The `decode` function has to accept a byte buffer and decode it into a value
  which will be returned."
  [snapshot column-family decode prefix-length target]
  (with-open [iter (kv/new-iterator snapshot column-family)]
    (kv/seek! iter (bs/to-byte-array target))
    (when (kv/valid? iter)
      (let [key-buf (bb/wrap (kv/key iter))]
        (when (prefix-matches? target prefix-length key-buf)
          (decode (bb/wrap (kv/value iter))))))))

(defn- read!
  "Reads from `iter` using the function `read!` and `buf`.

  When `buf` is too small, a new byte buffer will be created. Returns the
  byte buffer used to read."
  [read! buf iter]
  (bb/clear! buf)
  (let [size (long (read! iter buf))]
    (if (< (bb/capacity buf) size)
      (let [buf (bb/allocate (max size (bit-shift-left (bb/capacity buf) 1)))]
        (read! iter buf)
        buf)
      buf)))

(defn- read-key! [buf iter]
  (read! kv/key! buf iter))

(defn seek-key-filter
  "Returns a stateful transducer that filters it's inputs by finding a key in
  `column-family` that matches with regards of one of the `values`.

  It uses:
   * the `encode` function to fill a target byte buffer,
   * the `seek` function to decide to either seek forwards or backwards and
   * the `matches?` function to determine a match.

  The `encode` function gets the target byte buffer, the input, each value from
  `values` and returns a possibly enlarged target byte buffer. The target byte
  buffer will be cleared already.

  The `seek` function gets each value from `values` and has to return either
  `kv/seek-buffer!` or `kv/seek-for-prev-buffer!`.

  The `matches?` function gets the target byte buffer as returned by `encode`,
  the key byte buffer, the input, each value from `values` and returns some
  truthy value depending on whether the input matches.

  There are two helper functions that can be used to create matchers: the
  `target-length-matcher` and the `prefix-length-matcher`."
  [snapshot column-family seek matches? encode values]
  (fn [rf]
    (let [iter (kv/new-iterator snapshot column-family)
          encode (fn [target-buf input value]
                   (bb/clear! target-buf)
                   (encode target-buf input value))
          target-buf-state (volatile! (bb/allocate buffer-size))
          key-buf-state (volatile! (bb/allocate buffer-size))]
      (fn
        ([result]
         (.close ^AutoCloseable iter)
         (rf result))
        ([result input]
         (if (some
              (fn [value]
                (let [target-buf (vswap! target-buf-state encode input value)]
                  (bb/flip! target-buf)
                  ((seek value) iter target-buf)
                  (when (kv/valid? iter)
                    (bb/rewind! target-buf)
                    (let [key-buf (vswap! key-buf-state read-key! iter)]
                      (matches? target-buf key-buf input value)))))
              values)
           (rf result input)
           result))))))

(defn target-length-matcher
  "Returns a matcher that can be used with `seek-key-filter` that calls the
  `matches?` function if the whole target byte buffer is a prefix of the key
  byte buffer.

  The `matches?` function gets the key byte buffer, the input and the value. So
  the target byte buffer will be skipped."
  [matches?]
  (fn [target-buf key-buf input value]
    (when (prefix-matches* target-buf key-buf (bb/limit target-buf))
      (matches? key-buf input value))))

(defn prefix-length-matcher
  "Returns a matcher that can be used with `seek-key-filter` that calls the
  `matches?` function if a prefix of `prefix-length` of the target byte buffer
  is a prefix of the key byte buffer.

  The `matches?` function gets the key byte buffer, the input and the value. So
  the target byte buffer will be skipped."
  [prefix-length matches?]
  (fn [target-buf key-buf input value]
    (when (prefix-matches* target-buf key-buf (prefix-length input))
      (matches? key-buf input value))))

(defn- reduce-iter! [iter advance-fn rf init]
  (loop [ret init]
    (if (kv/valid? iter)
      (let [ret (rf ret iter)]
        (if (reduced? ret)
          (rf @ret)
          (do (advance-fn iter) (recur ret))))
      (rf ret))))

(defn- coll
  ([snapshot column-family xform]
   (reify
     IReduceInit
     (reduce [_ rf init]
       (with-open [iter (kv/new-iterator snapshot column-family)]
         (kv/seek-to-first! iter)
         (reduce-iter! iter kv/next! (xform (completing rf)) init)))
     Counted
     (count [coll]
       (.reduce coll coll/inc-rf 0))))
  ([snapshot column-family xform start-key]
   (reify
     IReduceInit
     (reduce [_ rf init]
       (with-open [iter (kv/new-iterator snapshot column-family)]
         (kv/seek! iter (bs/to-byte-array start-key))
         (reduce-iter! iter kv/next! (xform (completing rf)) init)))
     Counted
     (count [coll]
       (.reduce coll coll/inc-rf 0)))))

(defn- coll-prev [snapshot column-family xform start-key]
  (reify IReduceInit
    (reduce [_ rf init]
      (with-open [iter (kv/new-iterator snapshot column-family)]
        (kv/seek-for-prev! iter (bs/to-byte-array start-key))
        (reduce-iter! iter kv/prev! (xform (completing rf)) init)))))

(defn- key-reader
  "Returns a stateful transducer that will read the key from a downstream
  iterator and keeps track of the buffer used."
  []
  (fn [rf]
    (let [buf-state (volatile! (bb/allocate buffer-size))]
      (fn
        ([result] (rf result))
        ([result input]
         (rf result (vswap! buf-state read-key! input)))))))

(defn- key-decoder [decode]
  (comp
   (key-reader)
   (map decode)))

(defn keys
  "Returns a reducible collection of decoded keys of `column-family` starting
  with `start-key`.

  The `decode` function has to accept a byte buffer and decode it into an
  immutable value which will end up in the collection."
  [snapshot column-family decode start-key]
  (coll snapshot column-family (key-decoder decode) start-key))

(defn- take-while-prefix-matches
  "Returns a transducer that takes key buffers as long as their contents start
  with `prefix-length` bytes from `start-key`"
  [prefix-length start-key]
  (let [prefix-buf (bb/allocate prefix-length)]
    (bb/put-byte-string! prefix-buf (bs/subs start-key 0 prefix-length))
    (bb/flip! prefix-buf)
    (take-while
     (fn [buf]
       (let [mismatch (bb/mismatch buf prefix-buf)]
         (or (<= (long prefix-length) mismatch) (neg? mismatch)))))))

(defn- prefix-xf [start-key prefix-length decode]
  (comp
   (key-reader)
   (comp
    (take-while-prefix-matches prefix-length start-key)
    (map decode))))

(defn prefix-keys
  "Returns a reducible collection of decoded keys of `column-family` starting
  with `start-key` and ending when `prefix-length` bytes of `start-key` no
  longer match.

  The `decode` function has to accept a byte buffer and decode it into an
  immutable value which will end up in the collection."
  [snapshot column-family decode prefix-length start-key]
  (coll snapshot column-family (prefix-xf start-key prefix-length decode)
        start-key))

(defn prefix-keys-prev
  "Returns a reducible collection of decoded keys of `column-family` starting
  with `start-key` and ending when `prefix-length` bytes of `start-key` no
  longer match, iterating in reverse.

  The `decode` function has to accept a byte buffer and decode it into an
  immutable value which will end up in the collection."
  [snapshot column-family decode prefix-length start-key]
  (coll-prev snapshot column-family (prefix-xf start-key prefix-length decode)
             start-key))

(defn- read-value! [buf iter]
  (read! kv/value! buf iter))

(defn- entries-reader
  "Returns a stateful transducer that will read the key and value from a
  downstream iterator and keeps track of the buffers used."
  []
  (fn [rf]
    (let [kb-state (volatile! (bb/allocate buffer-size))
          vb-state (volatile! (bb/allocate buffer-size))]
      (fn
        ([result] (rf result))
        ([result iter]
         (rf result [(vswap! kb-state read-key! iter)
                     (vswap! vb-state read-value! iter)]))))))

(defn- entries-xf [xform]
  (comp
   (entries-reader)
   xform))

(defn entries
  "Returns a reducible collection of values created by the transducer `xform` of
  `column-family` optionally starting at `start-key`.

  The transducer `xform` will receive a tuple of key byte buffer and value
  byte buffer and has to emit an immutable value."
  ([snapshot column-family xform]
   (coll snapshot column-family (entries-xf xform)))
  ([snapshot column-family xform start-key]
   (coll snapshot column-family (entries-xf xform) start-key)))

(defn- entries-take-while-prefix-matches
  "Returns a transducer that takes tuples of key buffers and value buffers as
  long as their contents start with `prefix-length` bytes from `start-key`."
  [prefix-length start-key]
  (let [prefix-buf (bb/allocate prefix-length)]
    (bb/put-byte-string! prefix-buf (bs/subs start-key 0 prefix-length))
    (bb/flip! prefix-buf)
    (take-while
     (fn [[kb]]
       (let [full-limit (bb/limit kb)]
         (bb/set-limit! kb prefix-length)
         (let [res (= kb prefix-buf)]
           (bb/set-limit! kb full-limit)
           res))))))

(defn- entries-prefix-xf [start-key prefix-length xform]
  (comp
   (entries-reader)
   (comp
    (entries-take-while-prefix-matches start-key prefix-length)
    xform)))

(defn prefix-entries
  "Returns a reducible collection of decoded entries of `column-family` starting
  with `start-key` and ending when `prefix-length` bytes of `start-key` no
  longer match.

  The transducer `xform` will receive a tuple of key byte buffer and value
  byte buffer and has to emit an immutable value."
  [snapshot column-family xform prefix-length start-key]
  (coll snapshot column-family (entries-prefix-xf prefix-length start-key xform)
        start-key))
