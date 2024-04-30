(ns blaze.db.impl.index.type-search-param-token-full-resource
  "Functions for accessing the TypeSearchParamTokenFullResource index."
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

(defn- decode-id-hash-prefix
  "Returns a tuple of `[id hash-prefix]`."
  [buf]
  (bb/set-position! buf base-key-size)
  (bb/skip-null-terminated! buf)
  (bb/inc-position! buf codec/system-id-size)
  [(bs/from-byte-buffer-null-terminated! buf)
   (hash/prefix-from-byte-buffer! buf)])

(defn- key-size-value ^long [value]
  (+ base-key-size (bs/size value) 1))

(defn- key-size-value-system-id ^long [value]
  (+ (key-size-value value) codec/system-id-size))

(defn- key-size-value-system-id-id ^long [value id]
  (+ (key-size-value-system-id value) (bs/size id) 1))

(defn- encode-seek-key
  ([tb search-param-code-id value]
   (-> (bb/allocate (key-size-value value))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-null-terminated-byte-string! value)
       bb/flip!
       bs/from-byte-buffer!))
  ([tb search-param-code-id value system-id]
   (-> (bb/allocate (key-size-value-system-id value))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-null-terminated-byte-string! value)
       (bb/put-byte-string! system-id)
       bb/flip!
       bs/from-byte-buffer!))
  ([tb search-param-code-id value system-id start-id]
   (-> (bb/allocate (key-size-value-system-id-id value start-id))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-null-terminated-byte-string! value)
       (bb/put-byte-string! system-id)
       (bb/put-null-terminated-byte-string! start-id)
       bb/flip!
       bs/from-byte-buffer!)))

(defn prefix-keys
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `tb`, `search-param-code-id`, `value`, optional `system-id` and
  optional `start-id` and ending when `tb`, `search-param-code-id`, `value` and
  optional `system-id` is no longer a prefix."
  ([snapshot tb search-param-code-id value]
   (i/prefix-keys
    snapshot
    :type-search-param-token-full-resource-index
    decode-id-hash-prefix
    (key-size-value value)
    (encode-seek-key tb search-param-code-id value)))
  ([snapshot tb search-param-code-id value system-id]
   (i/prefix-keys
    snapshot
    :type-search-param-token-full-resource-index
    decode-id-hash-prefix
    (key-size-value-system-id value)
    (encode-seek-key tb search-param-code-id value system-id)))
  ([snapshot tb search-param-code-id value system-id start-id]
   (i/prefix-keys
    snapshot
    :type-search-param-token-full-resource-index
    decode-id-hash-prefix
    (key-size-value-system-id value)
    (encode-seek-key tb search-param-code-id value system-id start-id))))

(defn- encode-key [tb search-param-code-id value system-id id hash]
  (-> (bb/allocate (+ (key-size-value-system-id-id value id) hash/prefix-size))
      (bb/put-byte! tb)
      (bb/put-byte-string! search-param-code-id)
      (bb/put-null-terminated-byte-string! value)
      (bb/put-byte-string! system-id)
      (bb/put-null-terminated-byte-string! id)
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      bb/array))

(defn index-entry
  "Returns an entry of the TypeSearchParamTokenFullResource index build from
  `tb`, `search-param-code-id`, `value`, `system-id`, `id` and `hash`."
  [tb search-param-code-id value system-id id hash]
  [:type-search-param-token-full-resource-index
   (encode-key tb search-param-code-id value system-id id hash) bytes/empty])
