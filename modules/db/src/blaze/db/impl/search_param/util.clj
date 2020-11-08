(ns blaze.db.impl.search-param.util
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv :as kv]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec :as fhir-spec]
    [cognitect.anomalies :as anom])
  (:import
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)


(defn separate-op
  "Ordered search parameters of type number, date and quantity allow prefixes in
  search values. This function separates the possible prefix (operator) and
  returns a tuple of operator and value. The default operator :eq is returned if
  no prefix was given."
  [value]
  (if (re-matches #"^(eq|ne|gt|lt|ge|le|sa|eb|ap).*" value)
    [(keyword (subs value 0 2)) (subs value 2)]
    [:eq value]))


(defn format-skip-indexing-msg [value url type]
  (format "Skip indexing value `%s` of type `%s` for search parameter `%s` with type `%s` because the rule is missing."
          (pr-str value) (fhir-spec/fhir-type value) url type))


(def by-id-grouper
  "Transducer which groups `[id hash-prefix]` tuples by `id` and concatenates
  all hash-prefixes within each group, outputting `[id hash-prefixes]` tuples."
  (comp
    (partition-by (fn [[_ id]] (ByteBuffer/wrap id)))
    (map
      (fn group-hash-prefixes [[[_ id hash-prefix] & more]]
        [id (cons hash-prefix (map #(nth % 2) more))]))))


(defn non-deleted-resource-handle [context tid id]
  (when-let [handle (resource-as-of/resource-handle context tid id)]
    (when-not (rh/deleted? handle)
      handle)))


(defn- resource-missing-msg [tid id t]
  (format "Resource %s/%s doesn't exist at %d." (codec/tid->type tid)
          (codec/id id) t))


(defn resource-hash
  "Returns the hash of the resource with `tid` and `id` at `t`."
  [context tid id]
  (if-let [handle (non-deleted-resource-handle context tid id)]
    (rh/hash handle)
    (throw-anom ::anom/fault (resource-missing-msg tid id (:t context)))))


(defn- resource-handle-mapper* [context tid]
  (mapcat
    (fn [[id hash-prefixes]]
      (when-let [resource-handle (non-deleted-resource-handle context tid id)]
        [[resource-handle hash-prefixes]]))))


(def ^:private matches-hash-prefixes-filter
  (mapcat
    (fn [[resource-handle hash-prefixes]]
      (let [hash (hash/encode (rh/hash resource-handle))]
        (when (some #(bytes/starts-with? hash %) hash-prefixes)
          [resource-handle])))))


(defn resource-handle-mapper [context tid]
  (comp
    by-id-grouper
    (resource-handle-mapper* context tid)
    matches-hash-prefixes-filter))


(defn get-value [snapshot tid id hash c-hash]
  (kv/snapshot-get
    snapshot :resource-value-index
    (codec/resource-sp-value-key tid id hash c-hash)))


(defn prefix-seek [iter key]
  (kv/seek! iter key)
  (when (kv/valid? iter)
    (let [k (kv/key iter)]
      (when (bytes/starts-with? k key)
        k))))


(defn resource-sp-value-seek
  "Returns the first key on ResourceSearchParamValue index with starts with
  `tid`, `id`, `hash`, `c-hash` and optional `value`."
  ([iter tid id hash c-hash]
   (prefix-seek iter (codec/resource-sp-value-key tid id hash c-hash)))
  ([iter tid id hash c-hash value]
   (resource-sp-value-seek iter tid id hash c-hash value 0
                           (alength ^bytes value)))
  ([iter tid id hash c-hash value v-offset v-length]
   (prefix-seek iter (codec/resource-sp-value-key tid id hash c-hash value
                                                  v-offset v-length))))


(defn get-next-value
  ([iter tid id hash c-hash]
   (when-let [k (resource-sp-value-seek iter tid id hash c-hash)]
     (codec/resource-sp-value-key->value k)))
  ([iter tid id hash c-hash prefix p-offset p-length]
   (when-let [k (resource-sp-value-seek iter tid id hash c-hash
                                        prefix p-offset p-length)]
     (codec/resource-sp-value-key->value k))))


(defn sp-value-resource-keys
  "Returns a reducible collection of decoded SearchParamValueResource keys
  starting at `start-key`.

  Decoded keys consist of the triple [prefix id hash-prefix]."
  [iter start-key]
  (i/keys iter codec/decode-sp-value-resource-key start-key))


(defn sp-value-resource-keys-prev
  "Returns a reducible collection of decoded SearchParamValueResource keys
  starting at `start-key` in reverse order.

  Decoded keys consist of the triple [prefix id hash-prefix]."
  [iter start-key]
  (i/keys-prev iter codec/decode-sp-value-resource-key start-key))


(defn resource-sp-value-keys
  "Returns a reducible collection of decoded ResourceSearchParamValue keys
  starting at `start-key`.

  Decoded keys consist of the tuple [prefix value]."
  [iter start-key]
  (i/keys iter codec/decode-resource-sp-value-key start-key))


(defn resource-sp-value-keys-prev
  "Returns a reducible collection of decoded ResourceSearchParamValue keys
  starting at `start-key` in reverse order.

  Decoded keys consist of the tuple [prefix value]."
  [iter start-key]
  (i/keys-prev iter codec/decode-resource-sp-value-key start-key))


(defn prefix-keys
  ([iter start-key]
   (coll/eduction
     (take-while (fn [[prefix]] (bytes/starts-with? prefix start-key)))
     (sp-value-resource-keys iter start-key)))
  ([iter prefix-key start-key]
   (coll/eduction
     (take-while (fn [[prefix]] (bytes/starts-with? prefix prefix-key)))
     (sp-value-resource-keys iter start-key))))


(defn missing-expression-msg [url]
  (format "Unsupported search parameter with URL `%s`. Required expression is missing."
          url))
