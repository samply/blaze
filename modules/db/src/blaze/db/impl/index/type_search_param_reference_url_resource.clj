(ns blaze.db.impl.index.type-search-param-reference-url-resource
  "Functions for accessing the TypeSearchParamReferenceUrlResource index."
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

(defn- key-size
  (^long [url]
   (+ base-key-size (bs/size url) 1))
  (^long [url version]
   (+ (key-size url) (bs/size version) 1))
  (^long [url version id]
   (+ (key-size url version) (bs/size id) 1)))

(defn- key-size-version-prefix ^long [url version-prefix]
  (+ (key-size url) (bs/size version-prefix)))

(defn- encode-seek-key
  ([tb search-param-code-id url]
   (-> (bb/allocate (key-size url))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-null-terminated-byte-string! url)
       bb/flip!
       bs/from-byte-buffer!))
  ([tb search-param-code-id url version]
   (-> (bb/allocate (key-size url version))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-null-terminated-byte-string! url)
       (bb/put-null-terminated-byte-string! version)
       bb/flip!
       bs/from-byte-buffer!))
  ([tb search-param-code-id url version start-id]
   (-> (bb/allocate (key-size url version start-id))
       (bb/put-byte! tb)
       (bb/put-byte-string! search-param-code-id)
       (bb/put-null-terminated-byte-string! url)
       (bb/put-null-terminated-byte-string! version)
       (bb/put-null-terminated-byte-string! start-id)
       bb/flip!
       bs/from-byte-buffer!)))

(defn- decode-id-hash-prefix
  "Returns a tuple of `[id hash-prefix]`."
  [buf]
  (bb/set-position! buf base-key-size)
  (bb/skip-null-terminated! buf)
  (bb/skip-null-terminated! buf)
  [(bs/from-byte-buffer-null-terminated! buf)
   (hash/prefix-from-byte-buffer! buf)])

(defn prefix-keys
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `tb`, `search-param-code-id`, `url` and optional `start-version`
  and `start-id` and ending when `tb`, `search-param-code-id`, `url` is no
  longer a prefix."
  ([snapshot tb search-param-code-id url]
   (i/prefix-keys
    snapshot
    :type-search-param-reference-url-resource-index
    decode-id-hash-prefix
    (key-size url)
    (encode-seek-key tb search-param-code-id url)))
  ([snapshot tb search-param-code-id url start-version start-id]
   (i/prefix-keys
    snapshot
    :type-search-param-reference-url-resource-index
    decode-id-hash-prefix
    (key-size url)
    (encode-seek-key tb search-param-code-id url start-version start-id))))

(defn- encode-seek-key-version-prefix
  [tb search-param-code-id url version-prefix]
  (-> (bb/allocate (key-size-version-prefix url version-prefix))
      (bb/put-byte! tb)
      (bb/put-byte-string! search-param-code-id)
      (bb/put-null-terminated-byte-string! url)
      (bb/put-byte-string! version-prefix)
      bb/flip!
      bs/from-byte-buffer!))

(defn prefix-keys-version-prefix
  ([snapshot tb search-param-code-id url version-prefix]
   (i/prefix-keys
    snapshot
    :type-search-param-reference-url-resource-index
    decode-id-hash-prefix
    (key-size-version-prefix url version-prefix)
    (encode-seek-key-version-prefix tb search-param-code-id url version-prefix)))
  ([snapshot tb search-param-code-id url version-prefix start-version start-id]
   (i/prefix-keys
    snapshot
    :type-search-param-reference-url-resource-index
    decode-id-hash-prefix
    (key-size-version-prefix url version-prefix)
    (encode-seek-key tb search-param-code-id url start-version start-id))))

(defn prefix-keys-version
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `tb`, `search-param-code-id`, `url`, `version` and optional
  `start-id` and ending when `tb`, `search-param-code-id`, `url` and `version`
  is no longer a prefix."
  ([snapshot tb search-param-code-id url version]
   (i/prefix-keys
    snapshot
    :type-search-param-reference-url-resource-index
    decode-id-hash-prefix
    (key-size url version)
    (encode-seek-key tb search-param-code-id url version)))
  ([snapshot tb search-param-code-id url version start-id]
   (i/prefix-keys
    snapshot
    :type-search-param-reference-url-resource-index
    decode-id-hash-prefix
    (key-size url version)
    (encode-seek-key tb search-param-code-id url version start-id))))

(defn- encode-key [tb search-param-code-id url version id hash]
  (-> (bb/allocate (+ (key-size url version id) hash/prefix-size))
      (bb/put-byte! tb)
      (bb/put-byte-string! search-param-code-id)
      (bb/put-null-terminated-byte-string! url)
      (bb/put-null-terminated-byte-string! version)
      (bb/put-null-terminated-byte-string! id)
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      bb/array))

(defn index-entry
  "Returns an entry of the TypeSearchParamReferenceUrlResource index build from
  `tb`, `search-param-code-id`, `url`, `version`, `id` and `hash`."
  [tb search-param-code-id url version id hash]
  [:type-search-param-reference-url-resource-index
   (encode-key tb search-param-code-id url version id hash) bytes/empty])
