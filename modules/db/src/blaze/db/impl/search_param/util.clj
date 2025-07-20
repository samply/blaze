(ns blaze.db.impl.search-param.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.multi-version-id :as mvi]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.util :refer [str]]
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
  "Returns a stateful transducer which partitions multiple consecutive
  `SingleVersionId` instances by id, emitting one `MultiVersionId` instance per
  partition."
  (fn [rf]
    (let [acc (volatile! nil)]
      (fn
        ([result]
         (let [mvi @acc
               result (if (nil? mvi)
                        result
                        (do (vreset! acc nil)
                            (unreduced (rf result mvi))))]
           (rf result)))
        ([result svi]
         (let [mvi @acc
               id (svi/id svi)]
           (cond
             (nil? mvi)
             (do
               (vreset! acc (mvi/from-single-version-id svi))
               result)

             (= id (mvi/id mvi))
             (do
               (vreset! acc (mvi/conj mvi svi))
               result)

             :else
             (let [ret (rf result mvi)]
               (vreset! acc (if (reduced? ret) nil (mvi/from-single-version-id svi)))
               ret))))))))

(defn non-deleted-resource-handle [{:keys [snapshot t]} tid id]
  (when-let [handle (rao/resource-handle snapshot tid id t)]
    (when-not (rh/deleted? handle)
      handle)))

(defn- resource-handle-xf [{:keys [snapshot t]} tid]
  (rao/resource-handle-type-xf
   snapshot t tid mvi/id
   (fn [mvi handle] (mvi/matches-hash? mvi (rh/hash handle)))))

(defn resource-handle-mapper
  "Returns a transducer which partitions multiple consecutive `SingleVersionId`
  instances by `id` and maps them to a resource handle with `tid` if there is a
  current one with matching hash."
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
