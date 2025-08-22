(ns blaze.db.impl.search-param.string
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [clojure.string :as str]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defmulti index-entries
  "Returns index entries for `value` from a resource."
  {:arglists '([normalize value])}
  (fn [_ value] (fhir-spec/fhir-type value)))

(defn- normalize-string [s]
  (-> (str/trim s)
      (str/replace #"[\p{Punct}]" " ")
      (str/replace #"\s+" " ")
      str/lower-case))

(defn- index-entry [normalize value]
  (when-let [s (some-> value type/value normalize)]
    [nil (codec/string s)]))

(defmethod index-entries :fhir/string
  [normalize s]
  (some-> (index-entry normalize s) vector))

(defmethod index-entries :fhir/markdown
  [normalize s]
  (some-> (index-entry normalize s) vector))

(defmethod index-entries :fhir/Address
  [normalize {:keys [line city district state postalCode country]}]
  (coll/eduction
   (keep (partial index-entry normalize))
   (reduce conj line [city district state postalCode country])))

(defmethod index-entries :fhir/HumanName
  [normalize {:keys [family given]}]
  (coll/eduction
   (keep (partial index-entry normalize))
   (conj given family)))

(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "string")))

(defn- resource-value
  "Returns the value of the resource with `tid` and `id` according to the
  search parameter with `c-hash`."
  {:arglists '([batch-db c-hash tid id])}
  [{:keys [snapshot] :as batch-db} c-hash tid id]
  (r-sp-v/next-value snapshot (p/-resource-handle batch-db tid id) c-hash))

(defn- index-handles
  "Returns a reducible collection of index handles starting at `start-id`
  (optional)."
  {:arglists '([batch-db c-hash tid value] [batch-db c-hash tid value start-id])}
  ([{:keys [snapshot]} c-hash tid value]
   (sp-vr/index-handles snapshot c-hash tid (bs/size value) value))
  ([{:keys [snapshot] :as batch-db} c-hash tid _value start-id]
   (let [start-value (resource-value batch-db c-hash tid start-id)]
     (assert start-value)
     (sp-vr/index-handles snapshot c-hash tid (bs/size start-value) start-value
                          start-id))))

(defrecord SearchParamString [name type base code c-hash expression normalize]
  p/SearchParam
  (-compile-value [_ _ value]
    (codec/string (normalize value)))

  (-estimated-scan-size [_ _ _ _ _]
    (ba/unsupported))

  (-index-handles [_ batch-db tid _ compiled-value]
    (index-handles batch-db c-hash tid compiled-value))

  (-index-handles [_ batch-db tid _ compiled-value start-id]
    (index-handles batch-db c-hash tid compiled-value start-id))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db _ compled-values]
    (r-sp-v/value-prefix-filter (:snapshot batch-db) c-hash compled-values))

  (-single-version-id-matcher [_ batch-db tid _ compiled-values]
    (r-sp-v/single-version-id-value-prefix-filter
     (:snapshot batch-db) tid c-hash compiled-values))

  (-second-pass-filter [_ _ _])

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries normalize))))

(defmethod sc/search-param "string"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamString name type base code (codec/c-hash code) expression
                           (if (str/ends-with? url "phonetic")
                             u/soundex
                             normalize-string)))
    (ba/unsupported (u/missing-expression-msg url))))
