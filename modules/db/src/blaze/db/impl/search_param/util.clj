(ns blaze.db.impl.search-param.util
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.fhir.spec :as fhir-spec]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn separate-op
  "Ordered search parameters of type number, date and quantity allow prefixes in
  search values. This function separates the possible prefix (operator) and
  returns a tuple of operator and value. The default operator :eq is returned if
  no prefix was given."
  [value]
  (if (re-find #"^(eq|ne|gt|lt|ge|le|sa|eb|ap)" value)
    [(keyword (subs value 0 2)) (subs value 2)]
    [:eq value]))


(defn format-skip-indexing-msg [value url type]
  (format "Skip indexing value `%s` of type `%s` for search parameter `%s` with type `%s` because the rule is missing."
          (str value) (fhir-spec/fhir-type value) url type))


(def by-id-grouper
  "Transducer which groups `[id hash-prefix]` tuples by `id` and returns
  `[id tuples]` tuples."
  (comp
    (partition-by (fn [[id]] id))
    (map (fn [[[id] :as tuples]] [id tuples]))))


(defn non-deleted-resource-handle [resource-handle tid id]
  (when-let [{:keys [op] :as handle} (resource-handle tid id)]
    (when-not (identical? :delete op)
      handle)))


(defn- contains-hash-prefix? [triples hash-prefix]
  (reduce
    (fn [_ tuple]
      (when (= hash-prefix (nth tuple 1))
        (reduced true)))
    nil
    triples))


(defn- resource-handle-mapper* [{:keys [resource-handle]} tid]
  (keep
    (fn [[id tuples]]
      (when-let [{:keys [hash] :as resource-handle} (resource-handle tid id)]
        (let [hash-prefix (codec/hash-prefix hash)]
          (when (contains-hash-prefix? tuples hash-prefix)
            resource-handle))))))


(defn resource-handle-mapper [context tid]
  (comp
    by-id-grouper
    (resource-handle-mapper* context tid)))


(defn missing-expression-msg [url]
  (format "Unsupported search parameter with URL `%s`. Required expression is missing."
          url))


(defn reference-resource-handle-mapper
  "Returns a transducer that filters all upstream values for reference tid-id
  values with `tid`, returning the non-deleted resource handles of the
  referenced resources."
  [{:keys [resource-handle]} tid]
  (comp
    ;; other index entries are all v-hashes
    (filter #(< codec/v-hash-size (bs/size %)))
    (map bs/as-read-only-byte-buffer)
    ;; the type has to match
    (filter #(= ^long tid (bb/get-int! %)))
    (map bs/from-byte-buffer)
    (keep #(non-deleted-resource-handle resource-handle tid %))))
