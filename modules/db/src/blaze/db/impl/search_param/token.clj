(ns blaze.db.impl.search-param.token
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.core :as sc]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defmulti index-entries
  "Returns index entries for `value` from a resource."
  {:arglists '([resolve-id url value])}
  (fn [_ _ value] (fhir-spec/fhir-type value)))


(defmethod index-entries :fhir/id
  [_ _ id]
  (when-let [value (type/value id)]
    [[nil (codec/v-hash value)]]))


(defmethod index-entries :fhir/string
  [_ _ s]
  (when-let [value (type/value s)]
    [[nil (codec/v-hash value)]]))


(defmethod index-entries :fhir/uri
  [_ _ uri]
  (when-let [value (type/value uri)]
    [[nil (codec/v-hash value)]]))


(defmethod index-entries :fhir/boolean
  [_ _ boolean]
  (when-some [value (type/value boolean)]
    [[nil (codec/v-hash (str value))]]))


(defmethod index-entries :fhir/canonical
  [_ _ uri]
  (when-let [value (type/value uri)]
    [[nil (codec/v-hash value)]]))


(defmethod index-entries :fhir/code
  [_ _ code]
  ;; TODO: system
  (when-let [value (type/value code)]
    [[nil (codec/v-hash value)]]))


(defn token-coding-entries [{:keys [code system]}]
  (let [code (type/value code)
        system (type/value system)]
    (cond-> []
      code
      (conj [nil (codec/v-hash code)])
      system
      (conj [nil (codec/v-hash (str system "|"))])
      (and code system)
      (conj [nil (codec/v-hash (str system "|" code))])
      (and code (nil? system))
      (conj [nil (codec/v-hash (str "|" code))]))))


(defmethod index-entries :fhir/Coding
  [_ _ coding]
  (token-coding-entries coding))


(defmethod index-entries :fhir/CodeableConcept
  [_ _ {:keys [coding]}]
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
  [_ _ identifier]
  (identifier-entries nil identifier))


(defn- literal-reference-entries [resolve-id reference]
  (when-let [value (type/value reference)]
    (if-let [[type id] (u/split-literal-ref value)]
      (let [tid (codec/tid type)
            did (resolve-id tid id)]
        (cond-> [[nil (codec/v-hash id)]
                 [nil (codec/v-hash (str type "/" id))]]
          did (conj [nil (codec/tid-did tid did)])))
      [[nil (codec/v-hash value)]])))


(defmethod index-entries :fhir/Reference
  [resolve-id _ {:keys [reference identifier]}]
  (coll/eduction
    cat
    (cond-> []
      reference
      (conj (literal-reference-entries resolve-id reference))
      identifier
      (conj (identifier-entries "identifier" identifier)))))


(defmethod index-entries :fhir/ContactPoint
  [_ _ {:keys [value]}]
  (when-let [value (type/value value)]
    [[nil (codec/v-hash value)]]))


(defmethod index-entries :default
  [_ url value]
  (log/warn (u/format-skip-indexing-msg value url "token")))


(defn c-hash-w-modifier [c-hash code modifier]
  (if modifier
    (codec/c-hash (str code ":" modifier))
    c-hash))


(defn resource-keys!
  "Returns a reducible collection of [did hash-prefix] tuples starting at
  `start-did` (optional).

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([{:keys [svri]} c-hash tid value]
   (sp-vr/prefix-keys! svri c-hash tid value value))
  ([{:keys [svri]} c-hash tid value start-did]
   (sp-vr/prefix-keys! svri c-hash tid value value start-did)))


(defn matches? [{:keys [rsvi]} c-hash resource-handle value]
  (some? (r-sp-v/next-value! rsvi resource-handle c-hash value value)))


(defrecord SearchParamToken [name url type base code target c-hash expression]
  p/SearchParam
  (-compile-value [_ _modifier value]
    (codec/v-hash value))

  (-resource-handles [_ context tid modifier value]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys! context (c-hash-w-modifier c-hash code modifier) tid
                      value)))

  (-resource-handles [_ context tid modifier value start-did]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys! context (c-hash-w-modifier c-hash code modifier) tid value
                      start-did)))

  (-compartment-keys [_ context compartment tid value]
    (c-sp-vr/prefix-keys! (:csvri context) compartment c-hash tid value))

  (-matches? [_ context resource-handle modifier values]
    (let [c-hash (c-hash-w-modifier c-hash code modifier)]
      (some? (some #(matches? context c-hash resource-handle %) values))))

  (-compartment-ids [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction
        (keep
          (fn [value]
            (when (identical? :fhir/Reference (fhir-spec/fhir-type value))
              (when-let [reference (:reference value)]
                (some-> (u/split-literal-ref reference) (coll/nth 1))))))
        values)))

  (-index-values [search-param resource-id resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param resource-id) values)))

  (-index-value-compiler [_ resource-id]
    (mapcat (partial index-entries resource-id url))))


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
  (if expression
    (when-ok [expression (fhir-path/compile (fix-expr url expression))]
      (->SearchParamToken name url type base code target (codec/c-hash code) expression))
    (ba/unsupported (u/missing-expression-msg url))))


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
