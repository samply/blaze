(ns blaze.db.impl.search-param.util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.string :as str]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


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


(def ^:private by-did-grouper
  "Transducer which groups `[did hash-prefix]` tuples by `did`."
  (partition-by (fn [[did]] did)))


(defn non-deleted-resource-handle [resource-handle tid did]
  (when-let [handle (resource-handle tid did)]
    (when-not (rh/deleted? handle)
      handle)))


(defn- contains-hash-prefix-pred [resource-handle]
  (let [hash-prefix (hash/prefix (rh/hash resource-handle))]
    (fn [tuple] (= (long (coll/nth tuple 1)) hash-prefix))))


(defn- resource-handle-mapper* [{:keys [resource-handle]} tid]
  (keep
    (fn [tuples]
      (let [id (-> tuples (coll/nth 0) (coll/nth 0))]
        (when-let [handle (resource-handle tid id)]
          (when (some (contains-hash-prefix-pred handle) tuples)
            handle))))))


(defn resource-handle-mapper [context tid]
  (comp
    by-did-grouper
    (resource-handle-mapper* context tid)))


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
    (filter #(= (+ codec/tid-size codec/did-size) (bs/size %)))
    (map bs/as-read-only-byte-buffer)
    (keep #(non-deleted-resource-handle resource-handle (bb/get-int! %)
                                        (bb/get-long! %)))))


(defn split-literal-ref [^String s]
  (let [idx (.indexOf s 47)]
    (when (pos? idx)
      (let [type (.substring s 0 idx)]
        (when (.matches (re-matcher #"[A-Z]([A-Za-z0-9_]){0,254}" type))
          (let [id (.substring s (unchecked-inc-int idx))]
            (when (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" id))
              [type id])))))))


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
