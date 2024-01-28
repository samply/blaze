(ns blaze.db.impl.search-param.util
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.spec :as fhir-spec]
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

(defn non-deleted-resource-handle [resource-handle tid id]
  (when-let [handle (resource-handle tid id)]
    (when-not (rh/deleted? handle)
      handle)))

(defn- contains-hash-prefix-pred [resource-handle]
  (let [hash-prefix (hash/prefix (rh/hash resource-handle))]
    (fn [tuple] (= (long (coll/nth tuple 1)) hash-prefix))))

(defn- resource-handle-mapper* [{:keys [resource-handle]} tid]
  (keep
   (fn [[[id] :as tuples]]
     (when-let [handle (resource-handle tid id)]
       (when (coll/some (contains-hash-prefix-pred handle) tuples)
         handle)))))

(defn resource-handle-mapper
  "Transducer which groups `[id hash-prefix]` tuples by `id` and maps them to
  a resource handle with `tid` if there is a current one with matching hash
  prefix."
  [context tid]
  (comp
   by-id-grouper
   (resource-handle-mapper* context tid)))

(defn- id-groups-counter [{:keys [resource-handle]} tid]
  (fn [id-groups]
    (reduce
     (fn [sum [[id] :as tuples]]
       (if-let [handle (resource-handle tid id)]
         (cond-> sum
           (some (contains-hash-prefix-pred handle) tuples)
           inc)
         sum))
     0
     id-groups)))

(defn- resource-handle-counter
  "Returns a transducer that takes `[id hash-prefix]` tuples, groups them by
  id, partitions them and returns futures of the count of the found resource
  handles in each partition."
  [context tid]
  (let [id-groups-counter (id-groups-counter context tid)]
    (comp by-id-grouper
          (partition-all 1000)
          (map
           (fn [id-groups]
             (ac/supply-async #(id-groups-counter id-groups)))))))

(defn count-resource-handles [context tid resource-keys]
  (let [futures (into [] (resource-handle-counter context tid) resource-keys)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) + futures))))

(defn missing-expression-msg [url]
  (format "Unsupported search parameter with URL `%s`. Required expression is missing."
          url))

(defn reference-resource-handle-mapper
  "Returns a transducer that filters all upstream byte-string values for
  reference tid-id values, returning the non-deleted resource handles of the
  referenced resources."
  {:arglists '([context])}
  [{:keys [resource-handle]}]
  (comp
   ;; there has to be at least some bytes for the id
   (filter #(< codec/tid-size (bs/size %)))
   (map bs/as-read-only-byte-buffer)
   (keep
    #(let [tid (bb/get-int! %)
           id (bs/from-byte-buffer! %)]
       (non-deleted-resource-handle resource-handle tid id)))))

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
