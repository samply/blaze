(ns blaze.db.impl.search-param.string
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
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


(defn- resource-value
  "Returns the value of the resource with `tid` and `id` according to the
  search parameter with `c-hash`."
  [{:keys [rsvi] :as context} c-hash tid id]
  (let [hash (u/resource-hash context tid id)]
    (u/get-next-value rsvi tid id hash c-hash)))


(defn- start-key [context c-hash tid value start-id]
  (if start-id
    (let [start-value (resource-value context c-hash tid start-id)]
      (assert start-value)
      (codec/sp-value-resource-key c-hash tid start-value start-id))
    (codec/sp-value-resource-key c-hash tid value)))


(defn- take-while-prefix-matches [c-hash tid value]
  (let [prefix-key (codec/sp-value-resource-key c-hash tid value)]
    (take-while (fn [[prefix]] (bytes/starts-with? prefix prefix-key)))))


(defn- resource-keys
  "Returns a reducible collection of decoded SearchParamValueResource keys of
  values starting with prefix of `value` starting at `start-id` (optional).

  Decoded keys consist of the triple [prefix id hash-prefix]."
  [{:keys [svri] :as context} c-hash tid value start-id]
  (coll/eduction
    (take-while-prefix-matches c-hash tid value)
    (u/sp-value-resource-keys svri (start-key context c-hash tid value start-id))))


(defn- matches? [{:keys [rsvi]} c-hash tid id hash value]
  (u/resource-sp-value-seek rsvi tid id hash c-hash value))


(defrecord SearchParamString [name url type base code c-hash expression]
  p/SearchParam
  (-code [_]
    code)

  (-compile-values [_ values]
    (mapv (comp codec/string normalize-string) values))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys context c-hash tid value start-id)))

  (-compartment-keys [_ context compartment tid compiled-value]
    (let [{co-c-hash :c-hash co-res-id :res-id} compartment
          start-key (codec/compartment-search-param-value-key
                      co-c-hash co-res-id c-hash tid compiled-value)]
      (u/prefix-keys (:csvri context) start-key)))

  (-matches? [_ context tid id hash _ values]
    (some #(matches? context c-hash tid id hash %) values))

  (-index-entries [_ resolver hash resource _]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (let [{:keys [id]} resource
            type (clojure.core/name (fhir-spec/fhir-type resource))
            tid (codec/tid type)
            id-bytes (codec/id-bytes id)]
        (into
          []
          (mapcat
            (partial
              string-index-entries
              url
              (fn search-param-string-entry [value]
                (log/trace "search-param-value-entry" "string" code value type id hash)
                (let [value-bytes (codec/string value)]
                  [[:search-param-value-index
                    (codec/sp-value-resource-key
                      c-hash
                      tid
                      value-bytes
                      id-bytes
                      hash)
                    bytes/empty]
                   [:resource-value-index
                    (codec/resource-sp-value-key
                      tid
                      id-bytes
                      hash
                      c-hash
                      value-bytes)
                    bytes/empty]]))))
          values)))))


(defmethod sr/search-param "string"
  [{:keys [name url type base code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamString name url type base code (codec/c-hash code) expression))))
