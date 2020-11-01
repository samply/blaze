(ns blaze.db.impl.search-param.quantity
  (:require
    [blaze.anomaly :refer [throw-anom when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.util :as u]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defmulti quantity-index-entries
  "Returns index entries for `value` from a resource.

  The supplied function `entries-fn` takes a unit and a value and is used to
  create the actual index entries. Multiple such `entries-fn` results can be
  combined to one coll of entries."
  {:arglists '([url entries-fn value])}
  (fn [_ _ value] (fhir-spec/fhir-type value)))


(defmethod quantity-index-entries :fhir/Quantity
  [_ entries-fn {:keys [value system code unit]}]
  (let [value (type/value value)
        system (type/value system)
        code (type/value code)
        unit (type/value unit)]
    (cond-> []
      value
      (into (entries-fn nil value))
      code
      (into (entries-fn code value))
      (and unit (not= unit code))
      (into (entries-fn unit value))
      (and system code)
      (into (entries-fn (str system "|" code) value)))))


(defmethod quantity-index-entries :default
  [url _ value]
  (log/warn (u/format-skip-indexing-msg value url "quantity")))


(defn- unsupported-prefix-msg [op]
  (format "Unsupported prefix `%s` in search parameter of type quantity."
          (name op)))


(defn- compile-value [value]
  (let [[op value-and-unit] (u/separate-op value)
        [value unit] (str/split value-and-unit #"\|" 2)
        value (BigDecimal. ^String value)
        delta (.movePointLeft 0.5M (.scale value))]
    (case op
      (:eq :gt :lt :ge :le)
      [op
       (codec/quantity unit (- value delta))
       (codec/quantity unit value)
       (codec/quantity unit (+ value delta))]
      (throw-anom ::anom/unsupported (unsupported-prefix-msg op)))))


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


(defn- take-while-less-equal [c-hash tid value]
  (let [key (codec/sp-value-resource-key c-hash tid value)]
    (take-while (fn [[prefix]] (bytes/<= prefix key)))))


(defn- eq-keys
  "Returns a reducible collection of decoded SearchParamValueResource keys of
  values between `lower-bound` and `upper-bound` starting at `start-id`
  (optional).

  Decoded keys consist of the triple [prefix id hash-prefix]."
  [{:keys [svri] :as context} c-hash tid lower-bound upper-bound start-id]
  (coll/eduction
    (take-while-less-equal c-hash tid upper-bound)
    (u/sp-value-resource-keys svri (start-key context c-hash tid lower-bound
                                              start-id))))


(defn- gt-start-key [context c-hash tid value start-id]
  (if start-id
    (let [start-value (resource-value context c-hash tid start-id)]
      (assert start-value)
      (codec/sp-value-resource-key c-hash tid start-value start-id))
    (codec/sp-value-resource-key-for-prev c-hash tid value)))


(defn- unit-prefix-key [c-hash tid value]
  (let [unit-prefix (codec/unit-prefix value)]
    (codec/sp-value-resource-key c-hash tid unit-prefix)))


(defn- take-while-same-unit [c-hash tid value]
  (let [unit-prefix-key (unit-prefix-key c-hash tid value)]
    (take-while (fn [[prefix]] (bytes/starts-with? prefix unit-prefix-key)))))


(defn- gt-keys
  "Returns a reducible collection of decoded SearchParamValueResource keys of
  values greater than `value` starting at `start-id` (optional).

  Decoded keys consist of the triple [prefix id hash-prefix]."
  [{:keys [svri] :as context} c-hash tid value start-id]
  (coll/eduction
    (take-while-same-unit c-hash tid value)
    (u/sp-value-resource-keys svri (gt-start-key context c-hash tid value
                                                 start-id))))


(defn- lt-start-key [context c-hash tid value start-id]
  (if start-id
    (let [start-value (resource-value context c-hash tid start-id)]
      (assert start-value)
      (codec/sp-value-resource-key-for-prev c-hash tid start-value start-id))
    (codec/sp-value-resource-key c-hash tid value)))


(defn- lt-keys
  "Returns a reducible collection of decoded SearchParamValueResource keys of
  values less than `value` starting at `start-id` (optional).

  Decoded keys consist of the triple [prefix id hash-prefix]."
  [{:keys [svri] :as context} c-hash tid value start-id]
  (coll/eduction
    (take-while-same-unit c-hash tid value)
    (u/sp-value-resource-keys-prev svri (lt-start-key context c-hash tid value
                                                      start-id))))


(defn- ge-keys
  "Returns a reducible collection of decoded SearchParamValueResource keys of
  values greater or equal to `value` starting at `start-id` (optional).

  Decoded keys consist of the triple [prefix id hash-prefix]."
  [{:keys [svri] :as context} c-hash tid value start-id]
  (coll/eduction
    (take-while-same-unit c-hash tid  value)
    (u/sp-value-resource-keys svri (start-key context c-hash tid value
                                              start-id))))


(defn- le-start-key [context c-hash tid value start-id]
  (if start-id
    (let [start-value (resource-value context c-hash tid start-id)]
      (assert start-value)
      (codec/sp-value-resource-key-for-prev c-hash tid start-value start-id))
    (codec/sp-value-resource-key-for-prev c-hash tid value)))


(defn- le-keys
  "Returns a reducible collection of decoded SearchParamValueResource keys of
  values less or equal to `value` starting at `start-id` (optional).

  Decoded keys consist of the triple [prefix id hash-prefix]."
  [{:keys [svri] :as context} c-hash tid value start-id]
  (coll/eduction
    (take-while-same-unit c-hash tid  value)
    (u/sp-value-resource-keys-prev svri (le-start-key context c-hash tid value
                                                      start-id))))


(defn- resource-keys
  [context c-hash tid [op lower-bound exact-value upper-bound] start-id]
  (case op
    :eq (eq-keys context c-hash tid lower-bound upper-bound start-id)
    :gt (gt-keys context c-hash tid exact-value start-id)
    :lt (lt-keys context c-hash tid exact-value start-id)
    :ge (ge-keys context c-hash tid exact-value start-id)
    :le (le-keys context c-hash tid exact-value start-id)))


(defn- resource-sp-value [{:keys [rsvi]} tid id hash c-hash value]
  (let [start-key (codec/resource-sp-value-key tid id hash c-hash value)]
    (second (coll/first (u/resource-sp-value-keys rsvi start-key)))))


(defn eq-matches? [context c-hash tid id hash lower-bound upper-bound]
  (when-let [value (resource-sp-value context tid id hash c-hash lower-bound)]
    (bytes/<= value upper-bound)))


(defn gt-matches? [context c-hash tid id hash value]
  (when-let [found-value (resource-sp-value context tid id hash c-hash value)]
    (and (bytes/starts-with? found-value (codec/unit-prefix value))
         (bytes/> found-value value))))


(defn- resource-sp-value-prev [{:keys [rsvi]} tid id hash c-hash value]
  (let [start-key (codec/resource-sp-value-key tid id hash c-hash value)]
    (second (coll/first (u/resource-sp-value-keys-prev rsvi start-key)))))


(defn lt-matches? [context c-hash tid id hash value]
  (when-let [found-value (resource-sp-value-prev context tid id hash c-hash value)]
    (and (bytes/starts-with? found-value (codec/unit-prefix value))
         (bytes/< found-value value))))


(defn ge-matches? [context c-hash tid id hash value]
  (when-let [found-value (resource-sp-value context tid id hash c-hash value)]
    (bytes/starts-with? found-value (codec/unit-prefix value))))


(defn le-matches? [context c-hash tid id hash value]
  (when-let [found-value (resource-sp-value-prev context tid id hash c-hash value)]
    (bytes/starts-with? found-value (codec/unit-prefix value))))


(defn- matches?
  [context c-hash tid id hash [op lower-bound exact-value upper-bound]]
  (case op
    :eq (eq-matches? context c-hash tid id hash lower-bound upper-bound)
    :gt (gt-matches? context c-hash tid id hash exact-value)
    :lt (lt-matches? context c-hash tid id hash exact-value)
    :ge (ge-matches? context c-hash tid id hash exact-value)
    :le (le-matches? context c-hash tid id hash exact-value)))


(defrecord SearchParamQuantity [name url type base code c-hash expression]
  p/SearchParam
  (-code [_]
    code)

  (-compile-values [_ values]
    (mapv compile-value values))

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
              quantity-index-entries
              url
              (fn search-param-quantity-entry [unit value]
                (log/trace "search-param-value-entry" "quantity" code unit value type id hash)
                [[:search-param-value-index
                  (codec/sp-value-resource-key
                    c-hash
                    tid
                    (codec/quantity unit value)
                    id-bytes
                    hash)
                  bytes/empty]
                 [:resource-value-index
                  (codec/resource-sp-value-key
                    tid
                    id-bytes
                    hash
                    c-hash
                    (codec/quantity unit value))
                  bytes/empty]])))
          values)))))


(defmethod sr/search-param "quantity"
  [{:keys [name url type base code expression]}]
  (when-ok [expression (fhir-path/compile expression)]
    (->SearchParamQuantity name url type base code (codec/c-hash code) expression)))
