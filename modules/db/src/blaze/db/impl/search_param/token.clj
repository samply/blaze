(ns blaze.db.impl.search-param.token
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.async.comp :as ac]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.macros :refer [with-open-coll]]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.core :as sc]
    [blaze.db.impl.search-param.util :as u]
    [blaze.db.kv :as kv]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defmulti index-entries
  "Returns index entries for `value` from a resource."
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


(defmethod index-entries :fhir/boolean
  [_ boolean]
  (when-some [value (type/value boolean)]
    [[nil (codec/v-hash (str value))]]))


(defmethod index-entries :fhir/canonical
  [_ uri]
  (when-let [value (type/value uri)]
    [[nil (codec/v-hash value)]]))


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
    (if-let [[type id] (u/split-literal-ref value)]
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
  "Returns a reducible collection of [id hash-prefix] tuples starting at
  `start-id` (optional)."
  ([{:keys [snapshot]} c-hash tid value]
   (with-open-coll [svri (kv/new-iterator snapshot :search-param-value-index)]
     (sp-vr/prefix-keys! svri c-hash tid value value)))
  ([{:keys [snapshot]} c-hash tid value start-id]
   (with-open-coll [svri (kv/new-iterator snapshot :search-param-value-index)]
     (sp-vr/prefix-keys! svri c-hash tid value value start-id))))


(defn matches? [next-value c-hash resource-handle value]
  (some? (next-value resource-handle c-hash value value)))


(defrecord SearchParamToken [name url type base code target c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (codec/v-hash value))

  (-resource-handles [_ context tid modifier value]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys context (c-hash-w-modifier c-hash code modifier) tid
                     value)))

  (-resource-handles [_ context tid modifier value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys context (c-hash-w-modifier c-hash code modifier) tid value
                     start-id)))

  (-count-resource-handles [_ context tid modifier value]
    (u/count-resource-handles
      context tid
      (resource-keys context (c-hash-w-modifier c-hash code modifier) tid
                     value)))

  (-compartment-keys [_ context compartment tid value]
    (with-open-coll [csvri (kv/new-iterator (:snapshot context) :compartment-search-param-value-index)]
      (c-sp-vr/prefix-keys! csvri compartment c-hash tid value)))

  (-matches? [_ context resource-handle modifier values]
    (some? (some (partial matches? (:next-value context) (c-hash-w-modifier c-hash code modifier) resource-handle) values)))

  (-compartment-ids [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction
        (keep
          (fn [value]
            (when (identical? :fhir/Reference (fhir-spec/fhir-type value))
              (when-let [reference (type/value (:reference value))]
                (some-> (u/split-literal-ref reference) (coll/nth 1))))))
        values)))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))


(defrecord SearchParamId [name type code]
  p/SearchParam
  (-compile-value [_ _ value]
    (codec/id-byte-string value))

  (-resource-handles [_ context tid _ value]
    (some-> ((:resource-handle context) tid value) vector))

  (-resource-handles [_ context tid _ value start-id]
    (when (= value start-id)
      (some-> ((:resource-handle context) tid value) vector)))

  (-count-resource-handles [_ context tid _ value]
    (ac/completed-future (if ((:resource-handle context) tid value) 1 0)))

  (-sorted-resource-handles [_ context tid _]
    (rao/type-list context tid))

  (-sorted-resource-handles [_ context tid _ start-id]
    (rao/type-list context tid start-id))

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
        (->SearchParamToken name url type base code target (codec/c-hash code) expression))
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
