(ns blaze.db.impl.index.resource-search-param-reference-local
  "Functions for accessing the ResourceSearchParamReferenceLocal index."
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

(defn- key-size
  (^long [id]
   (+ codec/tb-size (bs/size id) 1 hash/prefix-size
      codec/search-param-code-id-size))
  (^long [id ref-id]
   (+ (key-size id) (bs/size ref-id) 1)))

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
  ([tb id hash search-param-code-id ref-id]
   (-> (encode-key-buf-1 (key-size id ref-id) tb id hash search-param-code-id)
       (bb/put-null-terminated-byte-string! ref-id)))
  ([tb id hash search-param-code-id ref-id ref-tb]
   (-> (encode-key-buf-1 (+ (key-size id ref-id) codec/tb-size)
                         tb id hash search-param-code-id)
       (bb/put-null-terminated-byte-string! ref-id)
       (bb/put-byte! ref-tb))))

(defn resource-search-param-value-encoder
  "Returns an encoder that can be used with `value-filter` that will encode the
  resource handle, search param with `search-param-code-id` and value it gets."
  [type-byte-index search-param-code-id]
  (fn [target-buf {:keys [tid id hash]} {:keys [ref-id ref-tb]}]
    (let [id (codec/id-byte-string id)
          target-buf (u/ensure-size target-buf
                                    (cond-> (key-size id ref-id)
                                      ref-tb
                                      (+ codec/tb-size)))]
      (encode-key-buf-1! target-buf (type-byte-index (codec/tid->type tid)) id
                         hash search-param-code-id)
      (cond-> (bb/put-null-terminated-byte-string! target-buf ref-id)
        ref-tb
        (bb/put-byte-string! ref-tb)))))

(defn value-filter
  "Returns a stateful transducer that filters resource handles depending on
  having one of `values` on search param with `search-param-code-id`."
  [snapshot type-byte-index search-param-code-id values]
  (i/seek-key-filter
   snapshot
   :resource-search-param-reference-local-index
   (fn [_] kv/seek-buffer!)
   (i/target-length-matcher (fn [_ _ _] true))
   (resource-search-param-value-encoder type-byte-index search-param-code-id)
   values))

(defn index-entry [tb id hash search-param-code-id ref-id ref-tb]
  [:resource-search-param-reference-local-index
   (bb/array (encode-key-buf tb id hash search-param-code-id ref-id ref-tb))
   bytes/empty])
