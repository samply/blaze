(ns blaze.db.impl.search-param.token
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
    [clojure.spec.alpha :as s]
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
              (into (entries-fn nil (codec/tid-id (codec/tid type)
                                                  (codec/id-bytes id))))))))))


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


(defn- index-token-entries
  [url code c-hash hash resource linked-compartments values]
  (let [{:keys [id]} resource
        type (name (fhir-spec/fhir-type resource))
        tid (codec/tid type)
        id-bytes (codec/id-bytes id)]
    (into
      []
      (mapcat
        (partial
          token-index-entries
          url
          (fn search-param-token-entry [modifier value]
            (log/trace "search-param-value-entry" "token" code type id
                       hash (codec/hex value))
            (let [c-hash (c-hash-w-modifier c-hash code modifier)]
              (into
                [[:search-param-value-index
                  (codec/sp-value-resource-key
                    c-hash
                    tid
                    value
                    id-bytes
                    hash)
                  bytes/empty]
                 [:resource-value-index
                  (codec/resource-sp-value-key
                    tid
                    id-bytes
                    hash
                    c-hash
                    value)
                  bytes/empty]]
                (map
                  (fn [[code id]]
                    [:compartment-search-param-value-index
                     (codec/compartment-search-param-value-key
                       (codec/c-hash code)
                       (codec/id-bytes id)
                       c-hash
                       tid
                       value
                       id-bytes
                       hash)
                     bytes/empty]))
                linked-compartments)))))
      values)))


(defn- resource-keys [{:keys [svri]} c-hash tid compiled-value start-id]
  (if start-id
    (u/prefix-keys
      svri
      (codec/sp-value-resource-key c-hash tid compiled-value)
      (codec/sp-value-resource-key c-hash tid compiled-value start-id))
    (u/prefix-keys
      svri
      (codec/sp-value-resource-key c-hash tid compiled-value))))


(defn- matches? [{:keys [rsvi]} c-hash tid id hash value]
  (u/resource-sp-value-seek rsvi tid id hash c-hash value))


(defrecord SearchParamToken [name url type base code c-hash expression]
  p/SearchParam
  (-code [_]
    code)

  (-compile-values [_ values]
    (mapv codec/v-hash values))

  (-resource-handles [_ context tid modifier value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys context (c-hash-w-modifier c-hash code modifier) tid value
                     start-id)))

  (-compartment-keys [_ context compartment tid compiled-value]
    (let [{co-c-hash :c-hash co-res-id :res-id} compartment
          start-key (codec/compartment-search-param-value-key
                      co-c-hash co-res-id c-hash tid compiled-value)]
      (u/prefix-keys (:csvri context) start-key)))

  (-matches? [_ context tid id hash modifier values]
    (let [c-hash (c-hash-w-modifier c-hash code modifier)]
      (some #(matches? context c-hash tid id hash %) values)))

  (-compartment-ids [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (into
        []
        (mapcat
          (fn [value]
            (case (clojure.core/type value)
              :fhir/Reference
              (let [{:keys [reference]} value]
                (when reference
                  (let [res (s/conform :blaze.fhir/local-ref reference)]
                    (when-not (s/invalid? res)
                      (rest res))))))))
        values)))

  (-index-entries [_ resolver hash resource linked-compartments]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (index-token-entries url code c-hash hash resource linked-compartments
                           values))))


(defmethod sr/search-param "token"
  [{:keys [name url type base code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))))


(defmethod sr/search-param "reference"
  [{:keys [name url type base code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))))


(defmethod sr/search-param "uri"
  [{:keys [name url type base code expression]}]
  (when expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamToken name url type base code (codec/c-hash code) expression))))
