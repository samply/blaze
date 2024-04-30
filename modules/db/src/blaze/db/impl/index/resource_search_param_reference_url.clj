(ns blaze.db.impl.index.resource-search-param-reference-url
  "Functions for accessing the ResourceSearchParamReferenceUrl index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.util :as u]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv]
   [blaze.fhir.hash :as hash]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn- decode-version [buf]
  (bb/inc-position! buf codec/tb-size)
  (bb/skip-null-terminated! buf)
  (bb/inc-position! buf hash/prefix-size)
  (bb/inc-position! buf codec/search-param-code-id-size)
  (bb/skip-null-terminated! buf)
  (bs/from-byte-buffer! buf))

(defn- key-size
  (^long [id]
   (+ codec/tb-size (bs/size id) 1 hash/prefix-size
      codec/search-param-code-id-size))
  (^long [id url]
   (+ (key-size id) (bs/size url) 1)))

(defn- encode-key-buf-1! [buf tb id hash search-param-code-id]
  (-> buf
      (bb/put-byte! tb)
      (bb/put-null-terminated-byte-string! id)
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      (bb/put-byte-string! search-param-code-id)))

(defn- encode-key-buf-1 [size tid id hash c-hash]
  (-> (bb/allocate size)
      (encode-key-buf-1! tid id hash c-hash)))

(defn- encode-key-buf
  ([tb id hash search-param-code-id]
   (encode-key-buf-1 (key-size id) tb id hash search-param-code-id))
  ([tb id hash search-param-code-id url]
   (-> (encode-key-buf-1 (key-size id url) tb id hash search-param-code-id)
       (bb/put-null-terminated-byte-string! url)))
  ([tb id hash search-param-code-id url version]
   (-> (encode-key-buf-1 (+ (key-size id url) (bs/size version))
                         tb id hash search-param-code-id)
       (bb/put-null-terminated-byte-string! url)
       (bb/put-byte-string! version))))

(defn- encode-key
  ([tb id hash search-param-code-id]
   (-> (encode-key-buf tb id hash search-param-code-id)
       bb/flip!
       bs/from-byte-buffer!))
  ([tb id hash search-param-code-id url]
   (-> (encode-key-buf tb id hash search-param-code-id url)
       bb/flip!
       bs/from-byte-buffer!))
  ([tb id hash search-param-code-id url version]
   (-> (encode-key-buf tb id hash search-param-code-id url version)
       bb/flip!
       bs/from-byte-buffer!)))

(defn version
  "Returns the version of the key encoded from `resource-handle`, 
  `search-param-code-id` and `url` or nil if not found."
  {:arglists
   '([snapshot type-byte-index resource-handle search-param-code-id url])}
  [snapshot type-byte-index {:keys [tid id hash]} search-param-code-id url]
  (let [id (codec/id-byte-string id)
        tb (type-byte-index (codec/tid->type tid))
        target (encode-key tb id hash search-param-code-id url)]
    (i/seek-key snapshot :resource-search-param-reference-url-index decode-version
                (key-size id url) target)))

(defn- resource-search-param-value-encoder
  "Returns an encoder that can be used with `value-filter` that will encode the
  resource handle, search param with `search-param-code-id` and value it gets."
  [type-byte-index search-param-code-id]
  (fn [target-buf {:keys [tid id hash]} {:keys [url version]}]
    (let [id (codec/id-byte-string id)
          target-buf (u/ensure-size target-buf
                                    (cond-> (key-size id url)
                                      version
                                      (+ (bs/size version))))]
      (encode-key-buf-1! target-buf (type-byte-index (codec/tid->type tid)) id
                         hash search-param-code-id)
      (cond-> (bb/put-null-terminated-byte-string! target-buf url)
        version
        (bb/put-byte-string! version)))))

(defn value-filter
  "Returns a stateful transducer that filters resource handles depending on
  having one of `values` on search param with `search-param-code-id`."
  [snapshot type-byte-index search-param-code-id values]
  (i/seek-key-filter
   snapshot
   :resource-search-param-reference-url-index
   (fn [_] kv/seek-buffer!)
   (i/target-length-matcher (fn [_ _ _] true))
   (resource-search-param-value-encoder type-byte-index search-param-code-id)
   values))

(defn index-entry [tb id hash search-param-code-id url version]
  [:resource-search-param-reference-url-index
   (bb/array (encode-key-buf tb id hash search-param-code-id url version))
   bytes/empty])
