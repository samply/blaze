(ns blaze.db.impl.index.resource-search-param-value
  "Functions for accessing the ResourceSearchParamValue index."
  (:require
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long key-buffer-capacity
  "Most search param value keys should fit into this size."
  128)


(defn- decode-value
  "Decodes the value from the key."
  ([] (bb/allocate-direct key-buffer-capacity))
  ([buf]
   (bb/set-position! buf (+ (bb/position buf) codec/tid-size))
   (let [^long id-size (bb/size-up-to-null buf)]
     (bb/set-position! buf (+ (bb/position buf) id-size 1 codec/hash-prefix-size
                              codec/c-hash-size))
     (bs/from-byte-buffer buf))))


(defn- key-size ^long [id]
  (+ codec/tid-size 1 (bs/size id) codec/hash-prefix-size codec/c-hash-size))


(defn- encode-key-buf-1 [size tid id hash c-hash]
  (-> (bb/allocate size)
      (bb/put-int! tid)
      (bb/put-byte-string! id)
      (bb/put-byte! 0)
      (bb/put-byte-string! (codec/hash-prefix hash))
      (bb/put-int! c-hash)))


(defn- encode-key-buf
  ([tid id hash c-hash]
   (encode-key-buf-1 (key-size id) tid id hash c-hash))
  ([tid id hash c-hash value]
   (-> (encode-key-buf-1 (+ (key-size id) (bs/size value)) tid id hash c-hash)
       (bb/put-byte-string! value))))


(defn- encode-key
  ([tid id hash c-hash]
   (-> (encode-key-buf tid id hash c-hash)
       (bb/flip!)
       (bs/from-byte-buffer)))
  ([tid id hash c-hash value]
   (-> (encode-key-buf tid id hash c-hash value)
       (bb/flip!)
       (bs/from-byte-buffer))))


(defn next-value!
  "Returns the decoded value of the key that is at or past the key encoded from
  `resource-handle`, `c-hash` and `value` and still starts with `prefix-value`."
  {:arglists
   '([iter resource-handle c-hash]
     [iter resource-handle c-hash prefix-value value])}
  ([iter {:keys [tid id hash]} c-hash]
   (let [id (codec/id-byte-string id)
         key (encode-key tid id hash c-hash)]
     (coll/first (i/prefix-keys! iter key decode-value key))))
  ([iter {:keys [tid id hash]} c-hash prefix-value value]
   (let [id (codec/id-byte-string id)
         prefix-key (encode-key tid id hash c-hash prefix-value)
         start-key (encode-key tid id hash c-hash value)]
     (coll/first (i/prefix-keys! iter prefix-key decode-value start-key)))))


(defn next-value-prev!
  "Returns the decoded value of the key that is at or before the key encoded
  from `resource-handle`, `c-hash` and `value` and still starts with
  `prefix-value`."
  {:arglists
   '([iter resource-handle c-hash prefix-value value])}
  [iter {:keys [tid id hash]} c-hash prefix-value value]
  (let [id (codec/id-byte-string id)
        prefix-key (encode-key tid id hash c-hash prefix-value)
        start-key (encode-key tid id hash c-hash value)]
    (coll/first (i/prefix-keys-prev! iter prefix-key decode-value start-key))))


(defn prefix-keys!
  "Returns a reducible collection of decoded values from keys starting at
  `value` (optional) and ending when the prefix of `tid`, `id`, `hash` and
  `c-hash` is no longer a prefix of the keys processed.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([iter tid id hash c-hash]
   (let [key (encode-key tid id hash c-hash)]
     (i/prefix-keys! iter key decode-value key)))
  ([iter tid id hash c-hash value]
   (let [prefix-key (encode-key tid id hash c-hash)
         start-key (encode-key tid id hash c-hash value)]
     (i/prefix-keys! iter prefix-key decode-value start-key))))


(defn get-value
  "Special get for the value with partial key [tid id hash c-hash]."
  [snapshot tid id hash c-hash]
  (bs/from-byte-array
    (kv/snapshot-get
      snapshot :resource-value-index
      (bb/array (encode-key-buf tid id hash c-hash)))))


(defn index-entry [tid id hash c-hash value]
  [:resource-value-index
   (bb/array (encode-key-buf tid id hash c-hash value))
   bytes/empty])


(defn index-entry-special [tid id hash c-hash value]
  [:resource-value-index
   (bb/array (encode-key-buf tid id hash c-hash))
   (bs/to-byte-array value)])
