(ns blaze.db.impl.search-param.string
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
    [clj-fuzzy.phonetics :as phonetics]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defmulti string-index-entries (fn [_ _ value] (fhir-spec/fhir-type value)))


(defn- normalize-string [s]
  (-> s
      (str/trim)
      (str/replace #"[\p{Punct}]" " ")
      (str/replace #"\s+" " ")
      (str/lower-case)))


(defmethod string-index-entries :fhir/string
  [_ entries-fn s]
  (when-let [value (type/value s)]
    (entries-fn (normalize-string value))))


(defmethod string-index-entries :fhir/markdown
  [_ entries-fn s]
  (when-let [value (type/value s)]
    (entries-fn (normalize-string value))))


(defn- string-entries [entries-fn values]
  (into
    []
    (comp
      (remove nil?)
      (map normalize-string)
      (mapcat entries-fn))
    values))


(defmethod string-index-entries :fhir/Address
  [_ entries-fn {:keys [line city district state postalCode country]}]
  (string-entries entries-fn (conj line city district state postalCode country)))


(defmethod string-index-entries :fhir/HumanName
  [url entries-fn {:keys [family given]}]
  (if (str/ends-with? url "phonetic")
    (some-> family type/value phonetics/soundex entries-fn)
    (string-entries entries-fn (conj (mapv type/value given) (type/value family)))))


(defmethod string-index-entries :default
  [url _ value]
  (log/warn (u/format-skip-indexing-msg value url "string")))


(defn- resource-value!
  "Returns the value of the resource with `tid` and `id` according to the
  search parameter with `c-hash`.

  Changes the state of `context`. Calling this function requires exclusive
  access to `context`."
  {:arglists '([context c-hash tid id])}
  [{:keys [rsvi resource-handle]} c-hash tid id]
  (r-sp-v/next-value! rsvi (resource-handle tid id) c-hash))


(defn- resource-keys!
  "Returns a reducible collection of `[id hash-prefix]` tuples starting at
  `start-id` (optional).

  Changes the state of `context`. Calling this function requires exclusive
  access to `context`."
  {:arglists '([context c-hash tid value] [context c-hash tid value start-id])}
  ([{:keys [svri]} c-hash tid value]
   (sp-vr/prefix-keys! svri c-hash tid value value))
  ([{:keys [svri] :as context} c-hash tid _value start-id]
   (let [start-value (resource-value! context c-hash tid start-id)]
     (assert start-value)
     (sp-vr/prefix-keys! svri c-hash tid start-value start-value start-id))))


(defn- matches? [{:keys [rsvi]} c-hash resource-handle value]
  (some? (r-sp-v/next-value! rsvi resource-handle c-hash value value)))


(defrecord SearchParamString [name url type base code c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (codec/string (normalize-string value)))

  (-resource-handles [_ context tid _ value]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys! context c-hash tid value)))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys! context c-hash tid value start-id)))

  (-compartment-keys [_ context compartment tid value]
    (c-sp-vr/prefix-keys! (:csvri context) compartment c-hash tid value value))

  (-matches? [_ context resource-handle _ values]
    (some? (some #(matches? context c-hash resource-handle %) values)))

  (-index-values [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (into
        []
        (mapcat
          #(string-index-entries
             url
             (fn [value]
               [[nil (codec/string value)]])
             %))
        values))))


(defmethod sr/search-param "string"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamString name url type base code (codec/c-hash code) expression))
    {::anom/category ::anom/unsupported
     ::anom/message (u/missing-expression-msg url)}))
