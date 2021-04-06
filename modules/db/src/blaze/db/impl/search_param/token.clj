(ns blaze.db.impl.search-param.token
  (:require
    [blaze.anomaly :refer [when-ok]]
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
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defmulti token-index-entries
  "Returns index entries for `value` from a resource.

  The supplied function `entries-fn` takes a value and is used to create the
  actual index entries. Multiple such `entries-fn` results can be combined to
  one coll of entries."
  {:arglists '([url entries-fn value])}
  (fn [_ _ value] (fhir-spec/fhir-type value)))


(defmethod token-index-entries :fhir/id
  [_ entries-fn id]
  (when-let [value (type/value id)]
    (entries-fn nil (codec/v-hash value))))


(defmethod token-index-entries :fhir/string
  [_ entries-fn s]
  (when-let [value (type/value s)]
    (entries-fn nil (codec/v-hash value))))


(defmethod token-index-entries :fhir/uri
  [_ entries-fn uri]
  (when-let [value (type/value uri)]
    (entries-fn nil (codec/v-hash value))))


(defmethod token-index-entries :fhir/boolean
  [_ entries-fn boolean]
  (when-some [value (type/value boolean)]
    (entries-fn nil (codec/v-hash (str value)))))


(defmethod token-index-entries :fhir/canonical
  [_ entries-fn uri]
  (when-let [value (type/value uri)]
    (entries-fn nil (codec/v-hash value))))


(defmethod token-index-entries :fhir/code
  [_ entries-fn code]
  ;; TODO: system
  (when-let [value (type/value code)]
    (entries-fn nil (codec/v-hash value))))


(defn token-coding-entries [entries-fn {:keys [code system]}]
  (let [code (type/value code)
        system (type/value system)]
    (cond-> []
      code
      (into (entries-fn nil (codec/v-hash code)))
      system
      (into (entries-fn nil (codec/v-hash (str system "|"))))
      (and code system)
      (into (entries-fn nil (codec/v-hash (str system "|" code))))
      (and code (nil? system))
      (into (entries-fn nil (codec/v-hash (str "|" code)))))))


(defmethod token-index-entries :fhir/Coding
  [_ entries-fn coding]
  (token-coding-entries entries-fn coding))


(defmethod token-index-entries :fhir/CodeableConcept
  [_ entries-fn {:keys [coding]}]
  (into [] (mapcat #(token-coding-entries entries-fn %)) coding))


(defn- token-identifier-entries [entries-fn modifier {:keys [value system]}]
  (let [value (type/value value)
        system (type/value system)]
    (cond-> []
      value
      (into (entries-fn modifier (codec/v-hash value)))
      system
      (into (entries-fn modifier (codec/v-hash (str system "|"))))
      (and value system)
      (into (entries-fn modifier (codec/v-hash (str system "|" value))))
      (and value (nil? system))
      (into (entries-fn modifier (codec/v-hash (str "|" value)))))))


(defmethod token-index-entries :fhir/Identifier
  [_ entries-fn identifier]
  (token-identifier-entries entries-fn nil identifier))


(defn- token-literal-reference-entries [entries-fn reference]
  (when-let [value (type/value reference)]
    (let [res (s/conform :blaze.fhir/local-ref value)]
      (if (s/invalid? res)
        (entries-fn nil (codec/v-hash value))
        (let [[type id] res]
          (-> (entries-fn nil (codec/v-hash id))
              (into (entries-fn nil (codec/v-hash (str type "/" id))))
              (into (entries-fn nil (codec/tid-id
                                      (codec/tid type)
                                      (codec/id-byte-string id))))))))))


(defmethod token-index-entries :fhir/Reference
  [_ entries-fn {:keys [reference identifier]}]
  (cond-> []
    reference
    (into (token-literal-reference-entries entries-fn reference))
    identifier
    (into (token-identifier-entries entries-fn "identifier" identifier))))


(defmethod token-index-entries :fhir/ContactPoint
  [_ entries-fn {:keys [value]}]
  (when-let [value (type/value value)]
    (entries-fn nil (codec/v-hash value))))


(defmethod token-index-entries :default
  [url _ value]
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

  (-compartment-ids [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (into
        []
        (mapcat
          (fn [value]
            (case (fhir-spec/fhir-type value)
              :fhir/Reference
              (let [{:keys [reference]} value]
                (when reference
                  (let [res (s/conform :blaze.fhir/local-ref reference)]
                    (when-not (s/invalid? res)
                      (rest res))))))))
        values)))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (p/-compile-index-values search-param values)))

  (-compile-index-values [_ values]
    (into
      []
      (mapcat
        #(token-index-entries
           url
           (fn [modifier value]
             [[modifier value]])
           %))
      values)))


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
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile (fix-expr url expression))]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))
    {::anom/category ::anom/unsupported
     ::anom/message (u/missing-expression-msg url)}))


(defmethod sr/search-param "reference"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))
    {::anom/category ::anom/unsupported
     ::anom/message (u/missing-expression-msg url)}))


(defmethod sr/search-param "uri"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))
    {::anom/category ::anom/unsupported
     ::anom/message (u/missing-expression-msg url)}))
