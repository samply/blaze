(ns blaze.db.impl.index.search-param-value-resource
  "Functions for accessing the SearchParamValueResource index."
  (:refer-clojure :exclude [keys])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.iterators :as i]
   [blaze.fhir.hash :as hash])
  (:import
   [blaze.db.impl.index SearchParamValueResource]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn decode-key
  "Returns a tuple of `[prefix single-version-id]`.

  The prefix contains the c-hash, tid and value parts as encoded byte string."
  [buf]
  (SearchParamValueResource/decodeKey buf))

(defn keys
  "Returns a reducible collection of `[prefix single-version-id]` tuples
  starting at `start-key`.

  The prefix contains the c-hash, tid and value parts as encoded byte string."
  [snapshot start-key]
  (i/keys snapshot :search-param-value-index decode-key start-key))

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
       (bb/put-null-terminated-byte-string! value)
       (bb/put-byte-string! id)
       (bb/put-byte! (bs/size id))
       bb/flip!
       bs/from-byte-buffer!)))

(def ^:private max-hash-prefix
  #blaze/byte-string"FFFFFFFF")

(defn- encode-seek-key-for-prev
  "It is important to cover at least the hash prefix because it could be all
  binary ones. Other parts like the id will be never all binary ones."
  ([c-hash tid]
   (bs/concat (encode-seek-key c-hash tid) max-hash-prefix))
  ([c-hash tid value]
   (bs/concat (encode-seek-key c-hash tid value) max-hash-prefix))
  ([c-hash tid value id]
   (bs/concat (encode-seek-key c-hash tid value id) max-hash-prefix)))

(defn decode-value-single-version-id
  "Decodes a tuple of `[value single-version-id]` from `buf`."
  [buf]
  (SearchParamValueResource/decodeValueSingleVersionId buf))

(defn all-keys
  "Returns a reducible collection of `[value single-version-id]` tuples of the
  whole range prefixed with `c-hash` and `tid` starting with `start-value` and
  `start-id` (optional)."
  ([snapshot c-hash tid]
   (let [prefix (encode-seek-key c-hash tid)]
     (i/prefix-keys snapshot :search-param-value-index
                    decode-value-single-version-id (bs/size prefix) prefix)))
  ([snapshot c-hash tid start-value start-id]
   (let [start-key (encode-seek-key c-hash tid start-value start-id)]
     (i/prefix-keys snapshot :search-param-value-index
                    decode-value-single-version-id base-key-size start-key))))

(defn all-keys-prev
  "Returns a reducible collection of decoded `[value single-version-id]` tuples
  of the whole range prefixed with `c-hash` and `tid` starting with
  `start-value` and `start-id` (optional), iterating in reverse."
  ([snapshot c-hash tid]
   (i/prefix-keys-prev
    snapshot
    :search-param-value-index
    decode-value-single-version-id
    base-key-size
    (encode-seek-key-for-prev c-hash tid)))
  ([snapshot c-hash tid start-value start-id]
   (i/prefix-keys-prev
    snapshot
    :search-param-value-index
    decode-value-single-version-id
    base-key-size
    (encode-seek-key-for-prev c-hash tid start-value start-id))))

(defn prefix-keys-value
  "Returns a reducible collection of decoded `[value single-version-id]` tuples
  from keys starting at a key with `value-prefix` and ending when `c-hash` and
  `tid` no longer match."
  [snapshot c-hash tid value-prefix]
  (let [start-key (encode-seek-key c-hash tid value-prefix)]
    (i/prefix-keys snapshot :search-param-value-index
                   decode-value-single-version-id base-key-size start-key)))

(defn prefix-keys-value-prev [snapshot c-hash tid value-prefix]
  (i/prefix-keys-prev
   snapshot
   :search-param-value-index
   decode-value-single-version-id
   base-key-size
   (encode-seek-key c-hash tid value-prefix)))

(defn decode-single-version-id
  "Decodes a single-version-id from `buf`."
  [buf]
  (SearchParamValueResource/decodeSingleVersionId buf))

(defn prefix-keys
  "Returns a reducible collection of decoded single-version-ids from keys
  starting at `start-value` and optional `start-id` and ending when
  `prefix-length` bytes of `start-value` is no longer a prefix of the values
  processed."
  ([snapshot c-hash tid prefix-length start-value]
   (i/prefix-keys
    snapshot
    :search-param-value-index
    decode-single-version-id
    (+ base-key-size (long prefix-length))
    (encode-seek-key c-hash tid start-value)))
  ([snapshot c-hash tid prefix-length start-value start-id]
   (i/prefix-keys
    snapshot
    :search-param-value-index
    decode-single-version-id
    (+ base-key-size (long prefix-length))
    (encode-seek-key c-hash tid start-value start-id))))

(defn prefix-keys'
  "Returns a reducible collection of decoded single-version-ids from keys
  starting at `start-value` and ending when `prefix-length` bytes of
  `start-value` is no longer a prefix of the values processed."
  [snapshot c-hash tid prefix-length start-value]
  (i/prefix-keys
   snapshot
   :search-param-value-index
   decode-single-version-id
   (+ base-key-size (long prefix-length))
   (encode-seek-key-for-prev c-hash tid start-value)))

(defn prefix-keys-prev
  "Returns a reducible collection of decoded single-version-ids from keys
  starting at `start-value` and optional `start-id` and ending when
  `prefix-length` bytes of `start-value` is no longer a prefix of the values
  processed, iterating in reverse."
  ([snapshot c-hash tid prefix-length start-value]
   (i/prefix-keys-prev
    snapshot
    :search-param-value-index
    decode-single-version-id
    (+ base-key-size (long prefix-length))
    (encode-seek-key-for-prev c-hash tid start-value)))
  ([snapshot c-hash tid prefix-length start-value start-id]
   (i/prefix-keys-prev
    snapshot
    :search-param-value-index
    decode-single-version-id
    (+ base-key-size (long prefix-length))
    (encode-seek-key-for-prev c-hash tid start-value start-id))))

(defn prefix-keys-prev'
  "Returns a reducible collection of decoded single-version-ids from keys
  starting at `start-value` and ending when `prefix-length` bytes of
  `start-value` is no longer a prefix of the values processed, iterating in
  reverse."
  [snapshot c-hash tid prefix-length start-value]
  (i/prefix-keys-prev
   snapshot
   :search-param-value-index
   decode-single-version-id
   (+ base-key-size (long prefix-length))
   (encode-seek-key c-hash tid start-value)))

(defn encode-key [c-hash tid value id hash]
  (-> (bb/allocate (unchecked-add-int (key-size value id) hash/prefix-size))
      (bb/put-int! c-hash)
      (bb/put-int! tid)
      (bb/put-null-terminated-byte-string! value)
      (bb/put-byte-string! id)
      (bb/put-byte! (bs/size id))
      (hash/prefix-into-byte-buffer! hash)
      bb/array))

(defn index-entry
  "Returns an entry of the SearchParamValueResource index build from `c-hash`,
  `tid`, `value`, `id` and `hash`."
  [c-hash tid value id hash]
  [:search-param-value-index (encode-key c-hash tid value id hash) bytes/empty])
