(ns blaze.db.impl.index.resource-search-param-value
  "Functions for accessing the ResourceSearchParamValue index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.iterators :as i]
   [blaze.fhir.hash :as hash]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn- decode-value
  "Decodes the value from the key."
  [buf]
  (bb/set-position! buf (unchecked-add-int (bb/position buf) codec/tid-size))
  (let [id-size (long (bb/size-up-to-null buf))]
    (bb/set-position! buf (+ (bb/position buf) id-size 1 hash/prefix-size
                             codec/c-hash-size))
    (bs/from-byte-buffer! buf)))

(defn- key-size ^long [id]
  (+ codec/tid-size 1 (bs/size id) hash/prefix-size codec/c-hash-size))

(defn- encode-key-buf-1 [size tid id hash c-hash]
  (-> (bb/allocate size)
      (bb/put-int! tid)
      (bb/put-byte-string! id)
      (bb/put-byte! 0)
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      (bb/put-int! c-hash)))

(defn- encode-key-buf
  ([tid id hash c-hash]
   (encode-key-buf-1 (key-size id) tid id hash c-hash))
  ([tid id hash c-hash value]
   (-> (encode-key-buf-1 (+ (key-size id) (bs/size value)) tid id hash c-hash)
       (bb/put-byte-string! value))))

(defn- encode-key
  ([tid id hash c-hash]
   (-> (encode-key-buf tid id hash c-hash) bb/flip! bs/from-byte-buffer!))
  ([tid id hash c-hash value]
   (-> (encode-key-buf tid id hash c-hash value) bb/flip! bs/from-byte-buffer!)))

(defn next-value
  "Returns the decoded value of the key that is at or past the key encoded from
  `resource-handle`, `c-hash` and optional `value` with `value-prefix-length`
  bytes of value matching."
  {:arglists
   '([snapshot resource-handle c-hash]
     [snapshot resource-handle c-hash value-prefix-length value])}
  ([snapshot {:keys [tid id hash]} c-hash]
   (let [id (codec/id-byte-string id)
         target (encode-key tid id hash c-hash)]
     (i/seek-key snapshot :resource-value-index decode-value
                 (bs/size target) target)))
  ([snapshot {:keys [tid id hash]} c-hash value-prefix-length value]
   (let [id (codec/id-byte-string id)
         target (encode-key tid id hash c-hash value)]
     (i/seek-key snapshot :resource-value-index decode-value
                 (+ (- (bs/size target) (bs/size value)) (long value-prefix-length))
                 target))))

(defn value-prefix-exists?
  "Returns true iff a key encoded from `resource-handle`, `c-hash` and
  `value-prefix` exists."
  {:arglists '([snapshot resource-handle c-hash value-prefix])}
  [snapshot {:keys [tid id hash]} c-hash value-prefix]
  (let [id (codec/id-byte-string id)
        target (encode-key tid id hash c-hash value-prefix)]
    (i/contains-key-prefix? snapshot :resource-value-index target)))

(defn next-value-prev
  "Returns the decoded value of the key that is at or before the key encoded
  from `resource-handle`, `c-hash`, `value` and with `value-prefix-length` bytes
  of value matching."
  {:arglists '([snapshot resource-handle c-hash value-prefix-length value])}
  [snapshot {:keys [tid id hash]} c-hash value-prefix-length value]
  (let [id (codec/id-byte-string id)
        target (encode-key tid id hash c-hash value)]
    (i/seek-key-prev snapshot :resource-value-index
                     decode-value
                     (+ (- (bs/size target) (bs/size value)) (long value-prefix-length))
                     target)))

(defn prefix-keys
  "Returns a reducible collection of decoded values from keys starting at
  `tid`, `id`, `hash`, `c-hash` and `start-value` (optional) and ending when the
  prefix of `tid`, `id`, `hash`, `c-hash` and `prefix-length` bytes of
  `start-value` (optional) is no longer a prefix of the keys processed."
  ([snapshot tid id hash c-hash]
   (let [key (encode-key tid id hash c-hash)]
     (i/prefix-keys snapshot :resource-value-index decode-value (bs/size key)
                    key)))
  ([snapshot tid id hash c-hash prefix-length start-value]
   (let [start-key (encode-key tid id hash c-hash start-value)]
     (i/prefix-keys snapshot :resource-value-index decode-value
                    (+ (key-size id) (long prefix-length))
                    start-key))))

(defn index-entry [tid id hash c-hash value]
  [:resource-value-index
   (bb/array (encode-key-buf tid id hash c-hash value))
   bytes/empty])
