(ns blaze.db.impl.index.resource-search-param-value
  "Functions for accessing the ResourceSearchParamValue index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.byte-string-builder :as bsb]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv]
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

(defn- key-size* ^long [^long id-size]
  (+ codec/tid-size 1 id-size hash/prefix-size codec/c-hash-size))

(defn key-size ^long [id]
  (key-size* (codec/id-size id)))

(defn- encode-key-buf-1! [buf tid id hash-prefix c-hash]
  (-> buf
      (bb/put-int! tid)
      (bb/put-null-terminated-iso-8859-1-string! id)
      (bb/put-int! hash-prefix)
      (bb/put-int! c-hash)))

(defn- encode-resource-handle-key-buf-1!
  "Like `encode-key-buf-1!` but copies tid, id and hash prefix directly from
  `resource-handle` without allocating."
  [buf resource-handle c-hash]
  (-> buf
      (rh/tid-into-byte-buffer! resource-handle)
      (rh/id-into-byte-buffer! resource-handle)
      (bb/put-byte! 0)
      (rh/hash-prefix-into-byte-buffer! resource-handle)
      (bb/put-int! c-hash)))

(defn- encode-key-builder-1! [builder tid id hash-prefix c-hash]
  (-> builder
      (bsb/put-int! tid)
      (bsb/put-null-terminated-iso-8859-1-string! id)
      (bsb/put-int! hash-prefix)
      (bsb/put-int! c-hash)))

(defn- encode-key
  ([tid id hash-prefix c-hash]
   (-> (bsb/allocate (key-size id))
       (encode-key-builder-1! tid id hash-prefix c-hash)
       bsb/build))
  ([tid id hash-prefix c-hash value]
   (-> (bsb/allocate (+ (key-size id) (bs/size value)))
       (encode-key-builder-1! tid id hash-prefix c-hash)
       (bsb/put-byte-string! value)
       bsb/build)))

(defn next-value
  "Returns the decoded value of the key that is at or past the key encoded from
  `resource-handle`, `c-hash` and optional `value` with `value-prefix-length`
  bytes of value matching."
  {:arglists
   '([snapshot resource-handle c-hash]
     [snapshot resource-handle c-hash value-prefix-length value])}
  ([snapshot {:keys [tid id hash-prefix]} c-hash]
   (let [target (encode-key tid id hash-prefix c-hash)]
     (i/seek-key snapshot :resource-value-index decode-value
                 (bs/size target) target)))
  ([snapshot {:keys [tid id hash-prefix]} c-hash value-prefix-length value]
   (let [target (encode-key tid id hash-prefix c-hash value)]
     (i/seek-key snapshot :resource-value-index decode-value
                 (+ (- (bs/size target) (bs/size value)) (long value-prefix-length))
                 target))))

(defn value-filter
  "Returns a stateful transducer that filters inputs depending on having one of
  the `values` match via `matches?`.

  The optional `seek` function gets each value from `values` and has to return
  either `kv/seek-buffer!` or `kv/seek-for-prev-buffer!`.

  Inputs will be given to `encode` and the optional `value-prefix-length` function.
  Inputs can be resource handles or single-version-ids."
  ([snapshot encode matches? values]
   (i/seek-key-filter
    snapshot
    :resource-value-index
    (fn [_] kv/seek-buffer!)
    (i/target-length-matcher
     (fn [key-buf _ value]
       (matches? (decode-value key-buf) value)))
    encode
    values))
  ([snapshot seek encode matches? value-prefix-length values]
   (i/seek-key-filter
    snapshot
    :resource-value-index
    seek
    (i/prefix-length-matcher
     value-prefix-length
     (fn [key-buf _ value]
       (matches? (decode-value key-buf) value)))
    encode
    values)))

(defn- ensure-size [target-buf size]
  (if (< (bb/capacity target-buf) (long size))
    (bb/allocate (max (long size) (bit-shift-left (bb/capacity target-buf) 1)))
    target-buf))

(defn resource-handle-search-param-encoder
  "Returns an encoder that can be used with `value-filter` that will encode the
  resource handle and search param with `c-hash`."
  [c-hash]
  (fn [target-buf resource-handle _]
    (-> (ensure-size target-buf (key-size* (codec/id-size (:id resource-handle))))
        (encode-resource-handle-key-buf-1! resource-handle c-hash))))

(defn single-version-id-search-param-encoder
  "Returns an encoder that can be used with `value-filter` that will encode the
  single-version-id and search param with `tid` and `c-hash`."
  [tid c-hash]
  (fn [target-buf single-version-id _]
    (let [id (svi/id single-version-id)
          hash-prefix (svi/hash-prefix single-version-id)
          target-buf (ensure-size target-buf (key-size id))]
      (encode-key-buf-1! target-buf tid id hash-prefix c-hash))))

(defn resource-handle-search-param-value-encoder
  "Returns an encoder that can be used with `value-filter` that will encode the
  resource handle, search param with `c-hash` and value it gets."
  [c-hash]
  (fn [target-buf resource-handle value]
    (-> (ensure-size target-buf (+ (key-size* (codec/id-size (:id resource-handle)))
                                   (bs/size value)))
        (encode-resource-handle-key-buf-1! resource-handle c-hash)
        (bb/put-byte-string! value))))

(defn single-version-id-search-param-value-encoder
  "Returns an encoder that can be used with `value-filter` that will encode the
  single-version-id, search param with `c-hash` and value it gets."
  [tid c-hash]
  (fn [target-buf single-version-id value]
    (let [id (svi/id single-version-id)
          hash-prefix (svi/hash-prefix single-version-id)
          target-buf (ensure-size target-buf (+ (key-size id) (bs/size value)))]
      (encode-key-buf-1! target-buf tid id hash-prefix c-hash)
      (bb/put-byte-string! target-buf value))))

(defn value-prefix-filter
  "Returns a stateful transducer that filters resource handles depending on
  having one of the `value-prefixes` on search param with `c-hash`."
  [snapshot c-hash value-prefixes]
  (i/seek-key-filter
   snapshot
   :resource-value-index
   (fn [_] kv/seek-buffer!)
   (i/target-length-matcher (fn [_ _ _] true))
   (resource-handle-search-param-value-encoder c-hash)
   value-prefixes))

(defn single-version-id-value-prefix-filter
  "Returns a stateful transducer that filters single-version-ids depending on
  having one of the `value-prefixes` on search param with `c-hash`."
  [snapshot tid c-hash value-prefixes]
  (i/seek-key-filter
   snapshot
   :resource-value-index
   (fn [_] kv/seek-buffer!)
   (i/target-length-matcher (fn [_ _ _] true))
   (single-version-id-search-param-value-encoder tid c-hash)
   value-prefixes))

(defn- prefix-keys* [snapshot prefix-length start-key]
  (i/prefix-keys snapshot :resource-value-index decode-value prefix-length
                 start-key))

(defn prefix-keys
  "Returns a reducible collection of decoded values from keys starting at
  `tid`, `id`, `hash-prefix`, `c-hash` and `start-value` (optional) and ending
  when the prefix of `tid`, `id`, `hash-prefix`, `c-hash` and `prefix-length`
  bytes of `start-value` (optional) is no longer a prefix of the keys
  processed."
  ([snapshot tid id hash-prefix c-hash]
   (let [key (encode-key tid id hash-prefix c-hash)]
     (prefix-keys* snapshot (bs/size key) key)))
  ([snapshot tid id hash-prefix c-hash prefix-length start-value]
   (prefix-keys* snapshot (+ (key-size id) (long prefix-length))
                 (encode-key tid id hash-prefix c-hash start-value))))

(defn index-entry [tid id hash c-hash value]
  [:resource-value-index
   (-> (bsb/allocate (+ (key-size id) (bs/size value)))
       (encode-key-builder-1! tid id (hash/prefix hash) c-hash)
       (bsb/put-byte-string! value)
       bsb/to-bytes)
   bytes/empty])
