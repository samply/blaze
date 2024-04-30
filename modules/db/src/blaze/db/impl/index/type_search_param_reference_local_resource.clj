(ns blaze.db.impl.index.type-search-param-reference-local-resource
  "Functions for accessing the TypeSearchParamReferenceLocalResource index."
  (:refer-clojure :exclude [keys])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.iterators :as i]
   [blaze.fhir.hash :as hash]))

(set! *unchecked-math* :warn-on-boxed)

(def ^:const ^long base-key-size
  (+ codec/tb-size codec/search-param-code-id-size))

(defn- key-size-ref-id ^long [ref-id]
  (+ base-key-size (bs/size ref-id) 1))

(defn- key-size-ref-id-tb ^long [ref-id]
  (inc (key-size-ref-id ref-id)))

(defn- key-size-ref-id-tb-id ^long [ref-id id]
  (+ (key-size-ref-id-tb ref-id) (bs/size id) 1))

(defn- encode-seek-key
  ([tb search-param-code-id ref-id]
   (-> (bb/allocate (key-size-ref-id ref-id))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-null-terminated-byte-string! ref-id)
       bb/flip!
       bs/from-byte-buffer!))
  ([tb search-param-code-id ref-id ref-tb]
   (-> (bb/allocate (key-size-ref-id-tb ref-id))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-null-terminated-byte-string! ref-id)
       (bb/put-byte! ref-tb)
       bb/flip!
       bs/from-byte-buffer!))
  ([tb search-param-code-id ref-id ref-tb start-id]
   (-> (bb/allocate (key-size-ref-id-tb-id ref-id start-id))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-null-terminated-byte-string! ref-id)
       (bb/put-byte! ref-tb)
       (bb/put-null-terminated-byte-string! start-id)
       bb/flip!
       bs/from-byte-buffer!)))

(defn- decode-id-hash-prefix
  "Returns a tuple of `[id hash-prefix]`."
  [buf]
  (bb/set-position! buf base-key-size)
  (bb/skip-null-terminated! buf)
  (bb/inc-position! buf codec/tb-size)
  [(bs/from-byte-buffer-null-terminated! buf)
   (hash/prefix-from-byte-buffer! buf)])

(defn prefix-keys
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `tb`, `search-param-code-id`, `ref-id`, optional `ref-tb` and
  optional `start-id` and ending when `tb`, `search-param-code-id`, `ref-id` and
  optional `ref-tb` is no longer a prefix."
  ([snapshot tb search-param-code-id ref-id]
   (i/prefix-keys
    snapshot
    :type-search-param-reference-local-resource-index
    decode-id-hash-prefix
    (key-size-ref-id ref-id)
    (encode-seek-key tb search-param-code-id ref-id)))
  ([snapshot tb search-param-code-id ref-id ref-tb]
   (i/prefix-keys
    snapshot
    :type-search-param-reference-local-resource-index
    decode-id-hash-prefix
    (key-size-ref-id-tb ref-id)
    (encode-seek-key tb search-param-code-id ref-id ref-tb)))
  ([snapshot tb search-param-code-id ref-id ref-tb start-id]
   (i/prefix-keys
    snapshot
    :type-search-param-reference-local-resource-index
    decode-id-hash-prefix
    (key-size-ref-id-tb ref-id)
    (encode-seek-key tb search-param-code-id ref-id ref-tb start-id))))

(defn- key-size ^long [ref-id id]
  (+ (key-size-ref-id-tb-id ref-id id) hash/prefix-size))

(defn- encode-key [tb search-param-code-id ref-id ref-tb id hash]
  (-> (bb/allocate (key-size ref-id id))
      (bb/put-byte! tb)
      (bb/put-byte-string! search-param-code-id)
      (bb/put-null-terminated-byte-string! ref-id)
      (bb/put-byte! ref-tb)
      (bb/put-null-terminated-byte-string! id)
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      bb/array))

(defn index-entry
  "Returns an entry of the TypeSearchParamReferenceLocalResource index build from
  `tb`, `search-param-code-id`, `ref-id`, `ref-tb`, `id` and `hash`."
  [tb search-param-code-id ref-id ref-tb id hash]
  [:type-search-param-reference-local-resource-index
   (encode-key tb search-param-code-id ref-id ref-tb id hash) bytes/empty])
