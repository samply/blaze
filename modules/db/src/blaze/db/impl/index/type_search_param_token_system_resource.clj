(ns blaze.db.impl.index.type-search-param-token-system-resource
  "Functions for accessing the TypeSearchParamTokenSystemResource index."
  (:refer-clojure :exclude [keys])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.iterators :as i]
   [blaze.fhir.hash :as hash]))

(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long base-key-size
  (+ codec/tb-size codec/search-param-code-id-size))

(def ^:private ^:const ^long key-size-system-id
  (+ base-key-size codec/system-id-size))

(defn- decode-id-hash-prefix
  "Returns a tuple of `[id hash-prefix]`."
  [buf]
  (bb/set-position! buf key-size-system-id)
  [(bs/from-byte-buffer-null-terminated! buf)
   (hash/prefix-from-byte-buffer! buf)])

(defn- key-size-system-id-id ^long [id]
  (+ key-size-system-id (bs/size id) 1))

(defn- encode-seek-key
  ([tb search-param-code-id system-id]
   (-> (bb/allocate key-size-system-id)
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-byte-string! system-id)
       bb/flip!
       bs/from-byte-buffer!))
  ([tb search-param-code-id system-id start-id]
   (-> (bb/allocate (key-size-system-id-id start-id))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-byte-string! system-id)
       (bb/put-null-terminated-byte-string! start-id)
       bb/flip!
       bs/from-byte-buffer!)))

(defn prefix-keys
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `tb`, `search-param-code-id`, `system-id` and optional `start-id`
  and ending when `tb`, `search-param-code-id`, `system-id` is no longer a
  prefix."
  ([snapshot tb search-param-code-id system-id]
   (i/prefix-keys
    snapshot
    :type-search-param-token-system-resource-index
    decode-id-hash-prefix
    key-size-system-id
    (encode-seek-key tb search-param-code-id system-id)))
  ([snapshot tb search-param-code-id system-id start-id]
   (i/prefix-keys
    snapshot
    :type-search-param-token-system-resource-index
    decode-id-hash-prefix
    key-size-system-id
    (encode-seek-key tb search-param-code-id system-id start-id))))

(defn- encode-key [tb search-param-code-id system-id id hash]
  (-> (bb/allocate (+ (key-size-system-id-id id) hash/prefix-size))
      (bb/put-byte! tb)
      (bb/put-byte-string! search-param-code-id)
      (bb/put-byte-string! system-id)
      (bb/put-null-terminated-byte-string! id)
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      bb/array))

(defn index-entry
  "Returns an entry of the TypeSearchParamTokenSystemResource index build from
  `tb`, `search-param-code-id`, `system-id`, `id` and `hash`."
  [tb search-param-code-id system-id id hash]
  [:type-search-param-token-system-resource-index
   (encode-key tb search-param-code-id system-id id hash) bytes/empty])
