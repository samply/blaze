(ns blaze.db.impl.search-param.string
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
    [clj-fuzzy.phonetics :as phonetics]
    [clojure.string :as str]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defmulti index-entries
  "Returns index entries for `value` from a resource."
  {:arglists '([url value])}
  (fn [_ value] (fhir-spec/fhir-type value)))


(defn- normalize-string [s]
  (-> (str/trim s)
      (str/replace #"[\p{Punct}]" " ")
      (str/replace #"\s+" " ")
      str/lower-case))


(defn- index-entry
  ([value]
   (index-entry normalize-string value))
  ([f value]
   (when-let [s (type/value value)]
     [nil (codec/string (f s))])))


(defmethod index-entries :fhir/string
  [_ s]
  (some-> (index-entry s) vector))


(defmethod index-entries :fhir/markdown
  [_ s]
  (some-> (index-entry s) vector))


(defmethod index-entries :fhir/Address
  [_ {:keys [line city district state postalCode country]}]
  (coll/eduction
    (keep index-entry)
    (reduce conj line [city district state postalCode country])))


(defmethod index-entries :fhir/HumanName
  [url {:keys [family given]}]
  (if (str/ends-with? url "phonetic")
    (some-> (index-entry phonetics/soundex family) vector)
    (coll/eduction
      (keep index-entry)
      (conj given family))))


(defmethod index-entries :default
  [url value]
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

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))


(defmethod sr/search-param "string"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamString name url type base code (codec/c-hash code) expression))
    (ba/unsupported (u/missing-expression-msg url))))
