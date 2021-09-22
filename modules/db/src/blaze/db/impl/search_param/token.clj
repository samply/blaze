(ns blaze.db.impl.search-param.token
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.anomaly-spec]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.util :as u]
    [blaze.db.search-param-registry :as sr]
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


(defn resource-keys!
  "Returns a reducible collection of [id hash-prefix] tuples starting at
  `start-id` (optional).

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([{:keys [svri]} c-hash tid value]
   (sp-vr/prefix-keys! svri c-hash tid value value))
  ([{:keys [svri]} c-hash tid value start-id]
   (sp-vr/prefix-keys! svri c-hash tid value value start-id)))


(defn matches? [{:keys [rsvi]} c-hash resource-handle value]
  (some? (r-sp-v/next-value! rsvi resource-handle c-hash value value)))


(defn- reference-id [value]
  (some-> value :reference u/split-literal-ref (nth 1)))


(defrecord SearchParamToken [name url type base code c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (codec/v-hash value))

  (-resource-handles [_ context tid modifier value]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys! context (c-hash-w-modifier c-hash code modifier) tid
                      value)))

  (-resource-handles [_ context tid modifier value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys! context (c-hash-w-modifier c-hash code modifier) tid value
                      start-id)))

  (-compartment-keys [_ context compartment tid value]
    (c-sp-vr/prefix-keys! (:csvri context) compartment c-hash tid value value))

  (-matches? [_ context resource-handle modifier values]
    (let [c-hash (c-hash-w-modifier c-hash code modifier)]
      (some? (some #(matches? context c-hash resource-handle %) values))))

  (-compartment-ids [_ resource]
    (when-ok [values (fhir-path/eval expression [resource])]
      (into [] (keep reference-id) values)))

  (-index-values [search-param resource]
    (when-ok [values (fhir-path/eval expression [resource])]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))


(defn- fix-expr
  "https://github.com/samply/blaze/issues/366"
  [url expression]
  (case url
    "http://hl7.org/fhir/SearchParameter/Observation-component-value-concept"
    "Observation.component.value.ofType(CodeableConcept)"
    "http://hl7.org/fhir/SearchParameter/Observation-combo-value-concept"
    "(Observation.value as CodeableConcept) | Observation.component.value.ofType(CodeableConcept)"
    expression))


(defmethod sr/search-param "token"
  [{:keys [resolver]} {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile resolver (fix-expr url expression))]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))
    (ba/unsupported (u/missing-expression-msg url))))


(defmethod sr/search-param "reference"
  [{:keys [resolver]} {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile resolver expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))
    (ba/unsupported (u/missing-expression-msg url))))


(defmethod sr/search-param "uri"
  [{:keys [resolver]} {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile resolver expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))
    (ba/unsupported (u/missing-expression-msg url))))
