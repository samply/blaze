(ns blaze.db.impl.search-param.token
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [blaze.util :refer [str]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defmulti index-entries
  "Returns index entries for `value` from a resource.

  Index entries are `[modifier value include-in-compartments?]` triples."
  {:arglists '([url value])}
  (fn [_ value] (fhir-spec/fhir-type value)))

(defmethod index-entries :fhir/id
  [_ id]
  (when-let [value (type/value id)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/string
  [_ s]
  (when-let [value (type/value s)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/uri
  [_ uri]
  (when-let [value (type/value uri)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/url
  [_ url]
  (when-let [value (type/value url)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :fhir/boolean
  [_ boolean]
  (when-some [value (type/value boolean)]
    [[nil (codec/v-hash (str value))]]))

(defmethod index-entries :fhir/canonical
  [_ uri]
  (when-let [value (type/value uri)]
    (let [[url version-parts] (u/canonical-parts value)]
      (into
       [[nil (codec/v-hash value)]
        ["below" (codec/v-hash url)]]
       (map
        (fn [version-part]
          ["below" (codec/v-hash (str url "|" version-part))]))
       version-parts))))

(defmethod index-entries :fhir/code
  [_ code]
  ;; TODO: system
  (when-let [value (type/value code)]
    [[nil (codec/v-hash value) true]]))

(defn token-coding-entries [{:keys [code system]}]
  (let [code (type/value code)
        system (type/value system)]
    (cond-> []
      code
      (conj [nil (codec/v-hash code)])
      system
      (conj [nil (codec/v-hash (str system "|"))])
      (and code system)
      (conj [nil (codec/v-hash (str system "|" code)) true])
      (and code (nil? system))
      (conj [nil (codec/v-hash (str "|" code))]))))

(defmethod index-entries :fhir/Coding
  [_ coding]
  (token-coding-entries coding))

(defmethod index-entries :fhir/CodeableConcept
  [_ {:keys [coding]}]
  (coll/eduction (mapcat token-coding-entries) coding))

(defn- identifier-entries [modifier {:keys [value system]}]
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

(defmethod index-entries :fhir/Identifier
  [_ identifier]
  (identifier-entries nil identifier))

(defn- literal-reference-entries [reference]
  (when-let [value (type/value reference)]
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
  [_ {:keys [value]}]
  (when-let [value (type/value value)]
    [[nil (codec/v-hash value)]]))

(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "token")))

(defn c-hash-w-modifier [c-hash code modifier]
  (if modifier
    (codec/c-hash (str code ":" modifier))
    c-hash))

(defn resource-keys
  "Returns a reducible collection of `SingleVersionId` instances that have `value`
  starting at `start-id` (optional)."
  ([{:keys [snapshot]} c-hash tid value]
   (sp-vr/prefix-keys snapshot c-hash tid (bs/size value) value))
  ([{:keys [snapshot]} c-hash tid value start-id]
   (sp-vr/prefix-keys snapshot c-hash tid (bs/size value) value start-id)))

(defrecord SearchParamToken [name url type base code target c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (if (= "reference" type)
      (if-let [[type id] (fsr/split-literal-ref value)]
        (codec/tid-id (codec/tid type) (codec/id-byte-string id))
        (if (and (= 1 (count target)) (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" value)))
          (codec/tid-id (codec/tid (first target)) (codec/id-byte-string value))
          (codec/v-hash value)))
      (codec/v-hash value)))

  (-resource-handles [_ batch-db tid modifier value]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (resource-keys batch-db (c-hash-w-modifier c-hash code modifier) tid
                    value)))

  (-resource-handles [_ batch-db tid modifier value start-id]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (resource-keys batch-db (c-hash-w-modifier c-hash code modifier) tid value
                    start-id)))

  (-chunked-resource-handles [_ batch-db tid modifier value]
    (coll/eduction
     (u/resource-handle-chunk-mapper batch-db tid)
     (resource-keys batch-db (c-hash-w-modifier c-hash code modifier) tid
                    value)))

  (-compartment-keys [_ context compartment tid value]
    (c-sp-vr/prefix-keys (:snapshot context) compartment c-hash tid value))

  (-matcher [_ batch-db modifier values]
    (r-sp-v/value-prefix-filter (:snapshot batch-db)
                                (c-hash-w-modifier c-hash code modifier) values))

  (-compartment-ids [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction
       (keep
        (fn [value]
          (when (identical? :fhir/Reference (fhir-spec/fhir-type value))
            (when-let [reference (type/value (:reference value))]
              (when-let [[type id] (fsr/split-literal-ref reference)]
                (when (= "Patient" type)
                  id))))))
       values)))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defn- resource-handles
  ([batch-db c-hash tid value]
   (into
    []
    (u/resource-handle-mapper batch-db tid)
    (resource-keys batch-db c-hash tid (codec/v-hash value))))
  ([batch-db c-hash tid value start-id]
   (into
    []
    (u/resource-handle-mapper batch-db tid)
    (resource-keys batch-db c-hash tid (codec/v-hash value) start-id))))

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(defn- identifier-values [{:keys [value system]}]
  (let [value (type/value value)
        system (type/value system)]
    (cond-> []
      value
      (conj value)
      system
      (conj (str system "|"))
      (and value system)
      (conj (str system "|" value))
      (and value (nil? system))
      (conj (str "|" value)))))

(defn- matches-identifier-values? [db expression value-set resource-handle]
  (let [resource @(d/pull db resource-handle)
        values (fhir-path/eval noop-resolver expression resource)]
    (assert (not (ba/anomaly? values)))
    (some value-set (mapcat identifier-values values))))

(defrecord SearchParamTokenIdentifier [name url type base code target c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    value)

  (-resource-handles [_ batch-db tid modifier value]
    (let [c-hash (c-hash-w-modifier c-hash code modifier)
          resource-handles (resource-handles batch-db c-hash tid value)]
      (filterv (partial matches-identifier-values? batch-db expression #{value}) resource-handles)))

  (-resource-handles [_ batch-db tid modifier value start-id]
    (let [c-hash (c-hash-w-modifier c-hash code modifier)
          resource-handles (resource-handles batch-db c-hash tid value start-id)]
      (filterv (partial matches-identifier-values? batch-db expression #{value}) resource-handles)))

  (-chunked-resource-handles [search-param batch-db tid modifier value]
    [(p/-resource-handles search-param batch-db tid modifier value)])

  (-compartment-keys [_ _ _ _ _])

  (-matcher [_ batch-db modifier values]
    (comp
     (r-sp-v/value-prefix-filter (:snapshot batch-db)
                                 (c-hash-w-modifier c-hash code modifier)
                                 (mapv codec/v-hash values))
     (filter (partial matches-identifier-values? batch-db expression (set values)))))

  (-compartment-ids [_ _ _])

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defrecord SearchParamId [name type code]
  p/SearchParam
  (-compile-value [_ _ value]
    (codec/id-byte-string value))

  (-resource-handles [_ batch-db tid _ value]
    (some-> (u/non-deleted-resource-handle batch-db tid value) vector))

  (-resource-handles [sp batch-db tid modifier value start-id]
    (when (= value start-id)
      (p/-resource-handles sp batch-db tid modifier value)))

  (-sorted-resource-handles [_ batch-db tid _]
    (rao/type-list batch-db tid))

  (-sorted-resource-handles [_ batch-db tid _ start-id]
    (rao/type-list batch-db tid start-id))

  (-chunked-resource-handles [search-param batch-db tid modifier value]
    [(p/-resource-handles search-param batch-db tid modifier value)])

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
          (->SearchParamTokenIdentifier name url type base code target (codec/c-hash code) expression)
          (->SearchParamToken name url type base code target (codec/c-hash code) expression)))
      (ba/unsupported (u/missing-expression-msg url)))))

(defmethod sc/search-param "reference"
  [_ {:keys [name url type base code target expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code target (codec/c-hash code) expression))
    (ba/unsupported (u/missing-expression-msg url))))

(defmethod sc/search-param "uri"
  [_ {:keys [name url type base code target expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code target (codec/c-hash code) expression))
    (ba/unsupported (u/missing-expression-msg url))))
