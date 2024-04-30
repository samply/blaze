(ns blaze.db.impl.search-param.util
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.protocols :as p]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [clojure.string :as str])
  (:import
   [org.apache.commons.codec.language Soundex]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(defn separate-op
  "Ordered search parameters of type number, date and quantity allow prefixes in
  search values. This function separates the possible prefix (operator) and
  returns a tuple of operator and value. The default operator :eq is returned if
  no prefix was given."
  [value]
  (let [value (str/trim value)]
    (if (re-find #"^(eq|ne|gt|lt|ge|le|sa|eb|ap)" value)
      [(keyword (subs value 0 2)) (str/trim (subs value 2))]
      [:eq value])))

(defn format-skip-indexing-msg [value url type]
  (format "Skip indexing value `%s` of type `%s` for search parameter `%s` with type `%s` because the rule is missing."
          (str value) (fhir-spec/fhir-type value) url type))

(def by-id-grouper
  "Transducer which groups `[id hash-prefix]` tuples by `id`."
  (partition-by (fn [[id]] id)))

(defn non-deleted-resource-handle [{:keys [snapshot t]} tid id]
  (when-let [handle (rao/resource-handle snapshot tid id t)]
    (when-not (rh/deleted? handle)
      handle)))

(defn- contains-hash-prefix-pred [resource-handle]
  (let [hash-prefix (hash/prefix (rh/hash resource-handle))]
    (fn [tuple] (= (long (coll/nth tuple 1)) hash-prefix))))

(defn- resource-handle-xf [{:keys [snapshot t]} tid]
  (rao/resource-handle-type-xf
   snapshot t tid (fn [[[id]]] id)
   (fn [tuples handle] (coll/some (contains-hash-prefix-pred handle) tuples))))

(defn resource-handle-mapper
  "Returns a transducer which groups `[id hash-prefix]` tuples by `id` and maps
  them to a resource handle with `tid` if there is a current one with matching
  hash prefix."
  [batch-db tid]
  (comp
   by-id-grouper
   (resource-handle-xf batch-db tid)))

(defn resource-handle-chunk-mapper
  "Like `resource-handle-mapper` but emits chunks of reducible collections of
  matching resource handles.

  That chunks can be used to process the resource handle mapping in parallel."
  [batch-db tid]
  (comp
   by-id-grouper
   (partition-all 1000)
   (map #(coll/eduction (resource-handle-xf batch-db tid) %))))

(defn missing-expression-msg [url]
  (format "Unsupported search parameter with URL `%s`. Required expression is missing."
          url))

(defn reference-resource-handle-mapper
  "Returns a transducer that filters all upstream byte-string values for
  reference tid-id values, returning the non-deleted resource handles of the
  referenced resources."
  [{:keys [snapshot t]}]
  (comp
   ;; there has to be at least some bytes for the id
   (filter #(< codec/tid-size (bs/size %)))
   (map bs/as-read-only-byte-buffer)
   (map (fn [buf] [(bb/get-int! buf) (bs/from-byte-buffer! buf)]))
   (rao/resource-handle-xf snapshot t)
   (remove rh/deleted?)))

(defn invalid-decimal-value-msg [code value]
  (format "Invalid decimal value `%s` in search parameter `%s`." value code))

(defn unsupported-prefix-msg [code op]
  (format "Unsupported prefix `%s` in search parameter `%s`." (name op) code))

(defn eq-value [f ^BigDecimal decimal-value]
  (let [delta (.movePointLeft 0.5M (.scale decimal-value))]
    {:op :eq
     :lower-bound (f (.subtract decimal-value delta))
     :exact-value (f decimal-value)
     :upper-bound (f (.add decimal-value delta))}))

(let [soundex (Soundex.)]
  (defn soundex [s]
    (try
      (.soundex soundex s)
      (catch IllegalArgumentException _))))

(defn- version-parts [version]
  (when-let [parts (seq (str/split version #"\."))]
    (into [] (take 2) (reductions #(str %1 "." %2) parts))))

(defn canonical-parts
  "Takes a canonical URl with possible version after a `|` char and returns a
  tuple of the URL part and a collection of at most two version parts.

  That version parts are first the major version and second the major and minor
  version separated by a period.

  Example: \"url|1.2.3\" -> [\"url\" [\"1\" \"1.2\"]]"
  [canonical]
  (let [[url version] (str/split canonical #"\|")]
    [(or url "") (some-> version version-parts)]))

(defn c-hash-w-modifier [c-hash code modifier]
  (if modifier
    (codec/c-hash (str code ":" modifier))
    c-hash))

(defn index-entries
  "Returns reducible collection of index entries of `resource` with `hash` for
  `search-param` or an anomaly in case of errors."
  {:arglists '([search-param resolver linked-compartments hash resource])}
  [{:keys [code c-hash] :as search-param} resolver linked-compartments hash resource]
  (when-ok [triples (p/-index-values search-param resolver resource)]
    (let [{:keys [id]} resource
          type (name (fhir-spec/fhir-type resource))
          tid (codec/tid type)
          id (codec/id-byte-string id)
          linked-compartments
          (mapv
           (fn [[code comp-id]]
             [(codec/c-hash code)
              (codec/id-byte-string comp-id)])
           linked-compartments)]
      (coll/eduction
       (mapcat
        (fn index-entry [[modifier value include-in-compartments?]]
          (let [c-hash (c-hash-w-modifier c-hash code modifier)]
            (transduce
             (keep
              (fn index-compartment-entry [compartment]
                (when include-in-compartments?
                  (c-sp-vr/index-entry
                   compartment
                   c-hash
                   tid
                   value
                   id
                   hash))))
             conj
             [(sp-vr/index-entry c-hash tid value id hash)
              (r-sp-v/index-entry tid id hash c-hash value)]
             linked-compartments))))
       triples))))

(defn identifier-index-entries [modifier {:keys [value system]}]
  (let [value (type/value value)
        system (type/value system)]
    (cond-> []
      value
      (conj [modifier (codec/v-hash value)])
      system
      (conj [modifier (codec/v-hash (str system "|"))])
      (and value system)
      (conj [modifier (codec/v-hash (str system "|" value))])
      (and value (nil? system))
      (conj [modifier (codec/v-hash (str "|" value))]))))
