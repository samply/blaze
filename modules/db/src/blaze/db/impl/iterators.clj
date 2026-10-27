(ns blaze.db.impl.iterators
  "This namespace provides a reducible collection abstraction over key-value
  store iterators.

  The reducible collections can be used together with transducers to obtain a
  collection of desired values that originated from a scan over a key-value
  store iterator, without having to deal with the low level and stateful API
  of the key-value store iterators."
  (:refer-clojure :exclude [keys])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.kv :as kv])
  (:import
   [clojure.lang Counted IReduceInit]
   [java.lang AutoCloseable]
   [java.util Iterator]))

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

(defn- buf-state ^objects []
  (object-array [(bb/allocate buffer-size)]))

(defmacro ^:private swap-buf!
  "Calls `f` with the byte buffer held in `buf-state` and `args`, stores the
  result back into `buf-state` if it's a different byte buffer and returns it.

  The `buf-state` is a single-element object array created by `buf-state`. It
  is accessed with plain array reads and writes, without memory barriers, so it
  must be confined to a single thread.

  The function `f` usually returns the current byte buffer and only allocates a
  new one if it's too small, so the write is rarely needed. Being a macro, `f`
  is called directly at each call site."
  [buf-state f & args]
  `(let [buf-state# ~buf-state
         buf# (aget buf-state# 0)
         new-buf# (~f buf# ~@args)]
     (when-not (identical? buf# new-buf#)
       (aset buf-state# 0 new-buf#))
     new-buf#))

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
          target-buf-state (buf-state)
          key-buf-state (buf-state)]
      (fn
        ([result]
         (.close ^AutoCloseable iter)
         (rf result))
        ([result input]
         (if (some
              (fn [value]
                (let [target-buf (swap-buf! target-buf-state encode input value)]
                  (bb/flip! target-buf)
                  ((seek value) iter target-buf)
                  (when (kv/valid? iter)
                    (bb/rewind! target-buf)
                    (let [key-buf (swap-buf! key-buf-state read-key! iter)]
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
  `matches?` function if a prefix of prefix-length determined by the
  `prefix-length` function of the target byte buffer is a prefix of the key
  byte buffer.

  The `prefix-length` function gets the input from the `seek-key-filter`
  transducer.

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

(deftype KvEntryIterator [iter ^:unsynchronized-mutable advance]
  Iterator
  (hasNext [_]
    ;; advances only on the next call, because the transducer reads the
    ;; current entry from `iter` after `next` returned it
    (when advance
      (set! advance false)
      (kv/next! iter))
    (kv/valid? iter))
  (next [_]
    (set! advance true)
    iter)
  AutoCloseable
  (close [_]
    (.close ^AutoCloseable iter)))

(defn- kv-iterable
  "Returns an iterable whose iterators return the kv iterator itself, positioned
  at successive entries starting with `start-key`."
  [snapshot column-family start-key]
  (reify Iterable
    (iterator [_]
      (let [iter (kv/new-iterator snapshot column-family)]
        (kv/seek! iter (bs/to-byte-array start-key))
        (->KvEntryIterator iter false)))))

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
       (coll/count-reducible coll))))
  ([snapshot column-family xform start-key]
   (reify
     IReduceInit
     (reduce [_ rf init]
       (with-open [iter (kv/new-iterator snapshot column-family)]
         (kv/seek! iter (bs/to-byte-array start-key))
         (reduce-iter! iter kv/next! (xform (completing rf)) init)))
     Counted
     (count [coll]
       (coll/count-reducible coll))
     Iterable
     (iterator [_]
       (.iterator ^Iterable (coll/eduction xform (kv-iterable snapshot column-family start-key)))))))

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
    (let [buf-state (buf-state)]
      (fn
        ([result] (rf result))
        ([result input]
         (rf result (swap-buf! buf-state read-key! input)))))))

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

(defn- prefix-xf [start-key prefix-length xform]
  (comp
   (key-reader)
   (take-while-prefix-matches prefix-length start-key)
   xform))

(defn prefix-keys-xf
  "Returns a reducible collection of values created by the transducer `xform`
  from keys of `column-family` starting with `start-key` and ending when
  `prefix-length` bytes of `start-key` no longer match.

  The transducer `xform` will receive the key byte buffer and has to emit
  immutable values. The key byte buffer is reused for the next key."
  [snapshot column-family xform prefix-length start-key]
  (coll snapshot column-family (prefix-xf start-key prefix-length xform)
        start-key))

(defn prefix-keys-prev-xf
  "Like `prefix-keys-xf` but iterates in reverse, starting with `start-key` and
  ending when `prefix-length` bytes of `start-key` no longer match."
  [snapshot column-family xform prefix-length start-key]
  (coll-prev snapshot column-family (prefix-xf start-key prefix-length xform)
             start-key))

(defn prefix-keys
  "Returns a reducible collection of decoded keys of `column-family` starting
  with `start-key` and ending when `prefix-length` bytes of `start-key` no
  longer match.

  The `decode` function has to accept a byte buffer and decode it into an
  immutable value which will end up in the collection."
  [snapshot column-family decode prefix-length start-key]
  (prefix-keys-xf snapshot column-family (map decode) prefix-length start-key))

(defn prefix-keys-prev
  "Returns a reducible collection of decoded keys of `column-family` starting
  with `start-key` and ending when `prefix-length` bytes of `start-key` no
  longer match, iterating in reverse.

  The `decode` function has to accept a byte buffer and decode it into an
  immutable value which will end up in the collection."
  [snapshot column-family decode prefix-length start-key]
  (prefix-keys-prev-xf snapshot column-family (map decode) prefix-length
                       start-key))

(defn- read-value! [buf iter]
  (read! kv/value! buf iter))

(defn- entries-reader
  "Returns a stateful transducer that will read the key and value from a
  downstream iterator and keeps track of the buffers used."
  []
  (fn [rf]
    (let [kb-state (buf-state)
          vb-state (buf-state)]
      (fn
        ([result] (rf result))
        ([result iter]
         (rf result [(swap-buf! kb-state read-key! iter)
                     (swap-buf! vb-state read-value! iter)]))))))

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

(defn- entries-prefix-xf [prefix-length start-key xform]
  (comp
   (entries-reader)
   (comp
    (entries-take-while-prefix-matches prefix-length start-key)
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

(defn prefix-entries-prev
  "Like `prefix-entries` but iterates in reverse, starting with `start-key` and
  ending when `prefix-length` bytes of `start-key` no longer match.

  The transducer `xform` will receive a tuple of key byte buffer and value
  byte buffer and has to emit an immutable value."
  [snapshot column-family xform prefix-length start-key]
  (coll-prev snapshot column-family
             (entries-prefix-xf prefix-length start-key xform) start-key))
