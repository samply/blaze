(ns blaze.db.impl.search-param.token
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defmulti index-entries
  "Returns index entries for `value` from a resource.

  Index entries are `[modifier value include-in-compartments?]` triples."
  {:arglists '([url value])}
  (fn [_ value] (or (:fhir/type value) (system/type value))))

(defmethod index-entries :fhir/id
  [_ {:keys [value]}]
  (when value
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/string
  [_ {:keys [value]}]
  (when value
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/uri
  [_ {:keys [value]}]
  (when value
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/url
  [_ {:keys [value]}]
  (when value
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/boolean
  [_ {:keys [value]}]
  (when-not (nil? value)
    [[nil (codec/v-hash (str value))]]))

(defmethod index-entries :system/boolean
  [_ value]
  [[nil (codec/v-hash (str value))]])

(defmethod index-entries :fhir/canonical
  [_ {:keys [value]}]
  (when value
    (let [[url version-parts] (u/canonical-parts value)]
      (into
       [[nil (codec/v-hash value)]
        ["below" (codec/v-hash url)]]
       (map
        (fn [version-part]
          ["below" (codec/v-hash (str url "|" version-part))]))
       version-parts))))

(defmethod index-entries :fhir/code
  [_ {:keys [value]}]
  ;; TODO: system
  (when value
    [[nil (codec/v-hash value) true]]))

(defn token-coding-entries [{{code :value} :code {system :value} :system}]
  (cond-> []
    code
    (conj [nil (codec/v-hash code)])
    system
    (conj [nil (codec/v-hash (str system "|"))])
    (and code system)
    (conj [nil (codec/v-hash (str system "|" code)) true])
    (and code (nil? system))
    (conj [nil (codec/v-hash (str "|" code))])))

(defmethod index-entries :fhir/Coding
  [_ coding]
  (token-coding-entries coding))

(defmethod index-entries :fhir/CodeableConcept
  [_ {:keys [coding]}]
  (coll/eduction (mapcat token-coding-entries) coding))

(defn- identifier-entries
  [modifier {{:keys [value]} :value {system :value} :system}]
  (cond-> []
    value
    (conj [modifier (codec/v-hash value)])
    system
    (conj [modifier (codec/v-hash (str system "|"))])
    (and value system)
    (conj [modifier (codec/v-hash (str system "|" value))])
    (and value (nil? system))
    (conj [modifier (codec/v-hash (str "|" value))])))

(defmethod index-entries :fhir/Identifier
  [_ identifier]
  (identifier-entries nil identifier))

(defn- literal-reference-entries [{:keys [value]}]
  (when value
    (if-let [[type id] (fsr/split-literal-ref value)]
      [[nil (codec/v-hash id)]
       [nil (codec/v-hash (str type "/" id))]
       [nil (codec/tid-id (codec/tid type)
                          (codec/id-byte-string id))]]
      [[nil (codec/v-hash value)]])))

(defmethod index-entries :fhir/Reference
  [_ {:keys [reference identifier]}]
  (coll/eduction
   cat
   (cond-> []
     reference
     (conj (literal-reference-entries reference))
     identifier
     (conj (identifier-entries "identifier" identifier)))))

(defmethod index-entries :fhir/ContactPoint
  [_ {{:keys [value]} :value}]
  (when value
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "token")))

(defn- c-hash-w-modifier [c-hash code modifier]
  (if modifier
    (codec/c-hash (str code ":" modifier))
    c-hash))

(defn index-handles
  "Returns a reducible collection of index handles that have `value` starting at
  `start-id` (optional)."
  ([{:keys [snapshot]} c-hash tid value]
   (sp-vr/index-handles snapshot c-hash tid (bs/size value) value))
  ([{:keys [snapshot]} c-hash tid value start-id]
   (sp-vr/index-handles snapshot c-hash tid (bs/size value) value start-id)))

(defn- has-system? [value]
  (let [idx (str/index-of value "|")]
    (and idx (< 0 idx (count value)))))

(defrecord SearchParamToken [name url type base code target c-hash expression]
  p/WithOrderedIndexHandles
  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values]
    (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
      (u/union-index-handles (map index-handles compiled-values))))

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values start-id]
    (let [index-handles #(p/-index-handles search-param batch-db tid modifier % start-id)]
      (u/union-index-handles (map index-handles compiled-values))))

  p/SearchParam
  (-compile-value [_ _ value]
    (if (= "reference" type)
      (if-let [[type id] (fsr/split-literal-ref value)]
        (codec/tid-id (codec/tid type) (codec/id-byte-string id))
        (if (and (= 1 (count target)) (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" value)))
          (codec/tid-id (codec/tid (first target)) (codec/id-byte-string value))
          (codec/v-hash value)))
      (codec/v-hash value)))

  (-estimated-scan-size [_ batch-db tid modifier compiled-value]
    (let [c-hash (c-hash-w-modifier c-hash code modifier)]
      (sp-vr/estimated-scan-size (:kv-store batch-db) c-hash tid compiled-value)))

  (-index-handles [_ batch-db tid modifier compiled-value]
    (index-handles batch-db (c-hash-w-modifier c-hash code modifier) tid
                   compiled-value))

  (-index-handles [_ batch-db tid modifier compiled-value start-id]
    (index-handles batch-db (c-hash-w-modifier c-hash code modifier) tid
                   compiled-value start-id))

  (-supports-ordered-compartment-index-handles [_ values]
   ;; the CompartmentSearchParamValueResource index only contains values with systems
    (every? has-system? values))

  (-ordered-compartment-index-handles [_ batch-db compartment tid compiled-value]
    (c-sp-vr/index-handles (:snapshot batch-db) compartment c-hash tid compiled-value))

  (-ordered-compartment-index-handles [_ batch-db compartment tid compiled-value start-id]
    (c-sp-vr/index-handles (:snapshot batch-db) compartment c-hash tid compiled-value start-id))

  (-matcher [_ batch-db modifier compiled-values]
    (r-sp-v/value-prefix-filter
     (:snapshot batch-db) (c-hash-w-modifier c-hash code modifier)
     compiled-values))

  (-single-version-id-matcher [_ batch-db tid modifier compiled-values]
    (r-sp-v/single-version-id-value-prefix-filter
     (:snapshot batch-db) tid (c-hash-w-modifier c-hash code modifier)
     compiled-values))

  (-second-pass-filter [_ _ _])

  (-compartment-ids [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction
       (keep
        (fn [value]
          (when (identical? :fhir/Reference (:fhir/type value))
            (when-let [reference (:value (:reference value))]
              (when-let [[type id] (fsr/split-literal-ref reference)]
                (when (= "Patient" type)
                  id))))))
       values)))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(defn- identifier-values [{{:keys [value]} :value {system :value} :system}]
  (cond-> []
    value
    (conj value)
    system
    (conj (str system "|"))
    (and value system)
    (conj (str system "|" value))
    (and value (nil? system))
    (conj (str "|" value))))

(defn- matches-identifier-values? [db expression value-set resource-handle]
  (let [resource @(d/pull db resource-handle)
        values (fhir-path/eval noop-resolver expression resource)]
    (assert (not (ba/anomaly? values)))
    (some value-set (mapcat identifier-values values))))

(defrecord SearchParamTokenIdentifier [name url type base code target c-hash expression]
  p/WithOrderedIndexHandles
  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values]
    (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
      (u/union-index-handles (map index-handles compiled-values))))

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values start-id]
    (let [index-handles #(p/-index-handles search-param batch-db tid modifier % start-id)]
      (u/union-index-handles (map index-handles compiled-values))))

  p/SearchParam
  (-compile-value [_ _ value]
    (codec/v-hash value))

  (-estimated-scan-size [_ _ _ _ _]
    1)

  (-index-handles [_ batch-db tid modifier compiled-value]
    (index-handles batch-db (c-hash-w-modifier c-hash code modifier) tid
                   compiled-value))

  (-index-handles [_ batch-db tid modifier compiled-value start-id]
    (index-handles batch-db (c-hash-w-modifier c-hash code modifier) tid
                   compiled-value start-id))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db modifier compiled-values]
    (r-sp-v/value-prefix-filter
     (:snapshot batch-db) (c-hash-w-modifier c-hash code modifier)
     compiled-values))

  (-single-version-id-matcher [_ batch-db tid modifier compiled-values]
    (r-sp-v/single-version-id-value-prefix-filter
     (:snapshot batch-db) tid (c-hash-w-modifier c-hash code modifier)
     compiled-values))

  (-second-pass-filter [_ batch-db values]
    (filter (partial matches-identifier-values? batch-db expression (set values))))

  (-compartment-ids [_ _ _])

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defrecord SearchParamId [name type code]
  p/WithOrderedIndexHandles
  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values]
    (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
      (coll/eduction (mapcat index-handles) (sort compiled-values))))

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values start-id]
    (let [compiled-values (drop-while #(not= start-id %) (sort compiled-values))
          index-handles #(p/-index-handles search-param batch-db tid modifier %)]
      (condp = (count compiled-values)
        0 []
        1 (index-handles (first compiled-values))
        (coll/eduction (mapcat index-handles) compiled-values))))

  p/SearchParam
  (-compile-value [_ _ value]
    (codec/id-byte-string value))

  (-estimated-scan-size [_ _ _ _ _]
    1)

  (-index-handles [_ batch-db tid _ compiled-value]
    (or (some-> (u/non-deleted-resource-handle batch-db tid compiled-value)
                (svi/from-resource-handle)
                (ih/from-single-version-id)
                (vector))
        []))

  (-index-handles [sp batch-db tid modifier compiled-value start-id]
    (if (bs/<= start-id compiled-value)
      (p/-index-handles sp batch-db tid modifier compiled-value)
      []))

  (-sorted-index-handles [_ batch-db tid _]
    (coll/eduction
     (map ih/from-resource-handle)
     (rao/type-list batch-db tid)))

  (-sorted-index-handles [_ batch-db tid _ start-id]
    (coll/eduction
     (map ih/from-resource-handle)
     (rao/type-list batch-db tid start-id)))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-second-pass-filter [_ _ _])

  (-index-values [_ _ _]))

(defn- fix-expr
  "https://github.com/samply/blaze/issues/366"
  [url expression]
  (case url
    "http://hl7.org/fhir/SearchParameter/Observation-component-value-concept"
    "Observation.component.value.ofType(CodeableConcept)"
    "http://hl7.org/fhir/SearchParameter/Observation-combo-value-concept"
    "(Observation.value as CodeableConcept) | Observation.component.value.ofType(CodeableConcept)"
    expression))

(defmethod sc/search-param "token"
  [_ {:keys [name url type base code target expression]}]
  (if (= "_id" code)
    (->SearchParamId "_id" "id" "_id")
    (if expression
      (when-ok [expression (fhir-path/compile (fix-expr url expression))]
        (if (= "identifier" code)
          (->SearchParamTokenIdentifier name url type base code target
                                        (codec/c-hash code) expression)
          (->SearchParamToken name url type base code target (codec/c-hash code)
                              expression)))
      (ba/unsupported (u/missing-expression-msg url)))))

(defmethod sc/search-param "reference"
  [_ {:keys [name url type base code target expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code target (codec/c-hash code)
                          expression))
    (ba/unsupported (u/missing-expression-msg url))))

(defmethod sc/search-param "uri"
  [_ {:keys [name url type base code target expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code target (codec/c-hash code)
                          expression))
    (ba/unsupported (u/missing-expression-msg url))))
