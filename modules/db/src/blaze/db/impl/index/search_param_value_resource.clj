(ns blaze.db.impl.index.search-param-value-resource
  "Functions for accessing the SearchParamValueResource index."
  (:refer-clojure :exclude [keys])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.iterators :as i]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.kv :as kv]
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
     (i/prefix-keys
      snapshot
      :search-param-value-index
      decode-value-single-version-id
      (bs/size prefix)
      prefix)))
  ([snapshot c-hash tid start-value start-id]
   (i/prefix-keys
    snapshot
    :search-param-value-index
    decode-value-single-version-id
    base-key-size
    (encode-seek-key c-hash tid start-value start-id))))

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
    (i/prefix-keys
     snapshot
     :search-param-value-index
     decode-value-single-version-id
     base-key-size
     start-key)))

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

(defn- index-handles* [snapshot prefix-length seek-key]
  (coll/eduction
   u/by-id-grouper
   (i/prefix-keys
    snapshot
    :search-param-value-index
    decode-single-version-id
    (+ base-key-size (long prefix-length))
    seek-key)))

(defn index-handles
  "Returns a reducible collection of index handles from keys starting at
  `start-value` and optional `start-id` and ending when `prefix-length` bytes
  of `start-value` is no longer a prefix of the values processed."
  ([snapshot c-hash tid prefix-length start-value]
   (let [seek-key (encode-seek-key c-hash tid start-value)]
     (index-handles* snapshot prefix-length seek-key)))
  ([snapshot c-hash tid prefix-length start-value start-id]
   (let [seek-key (encode-seek-key c-hash tid start-value start-id)]
     (index-handles* snapshot prefix-length seek-key))))

(defn index-handles'
  "Returns a reducible collection of index handles from keys starting at
  `start-value` and ending when `prefix-length` bytes of `start-value` is no
  longer a prefix of the values processed."
  [snapshot c-hash tid prefix-length start-value]
  (coll/eduction
   u/by-id-grouper
   (i/prefix-keys
    snapshot
    :search-param-value-index
    decode-single-version-id
    (+ base-key-size (long prefix-length))
    (encode-seek-key-for-prev c-hash tid start-value))))

(defn index-handles-prev
  "Returns a reducible collection of index handles from keys starting at
  `start-value` and optional `start-id` and ending when `prefix-length` bytes
  of `start-value` is no longer a prefix of the values processed, iterating in
  reverse."
  ([snapshot c-hash tid prefix-length start-value]
   (coll/eduction
    u/by-id-grouper
    (i/prefix-keys-prev
     snapshot
     :search-param-value-index
     decode-single-version-id
     (+ base-key-size (long prefix-length))
     (encode-seek-key-for-prev c-hash tid start-value))))
  ([snapshot c-hash tid prefix-length start-value start-id]
   (coll/eduction
    u/by-id-grouper
    (i/prefix-keys-prev
     snapshot
     :search-param-value-index
     decode-single-version-id
     (+ base-key-size (long prefix-length))
     (encode-seek-key-for-prev c-hash tid start-value start-id)))))

(defn index-handles-prev'
  "Returns a reducible collection of index handles from keys starting at
  `start-value` and ending when `prefix-length` bytes of `start-value` is no
  longer a prefix of the values processed, iterating in reverse."
  [snapshot c-hash tid prefix-length start-value]
  (coll/eduction
   u/by-id-grouper
   (i/prefix-keys-prev
    snapshot
    :search-param-value-index
    decode-single-version-id
    (+ base-key-size (long prefix-length))
    (encode-seek-key c-hash tid start-value))))

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

(defn estimated-scan-size
  "Returns a relative estimation of the amount of work to do while scanning the
  SearchParamValueResource index with the prefix consisting of `c-hash`, `tid`
  and `value`.

  The metric is relative and unitless. It can be only used to compare the amount
  of scan work between different prefixes.

  Returns an anomaly if estimating the scan size isn't supported by `kv-store`."
  [kv-store c-hash tid value]
  (let [seek-key (encode-seek-key c-hash tid value)
        key-range [seek-key (bs/concat seek-key (bs/from-hex "FF"))]]
    (kv/estimate-scan-size kv-store :search-param-value-index key-range)))
