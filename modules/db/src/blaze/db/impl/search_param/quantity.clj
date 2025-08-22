(ns blaze.db.impl.search-param.quantity
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.kv :as kv]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defmulti index-entries
  "Returns index entries for `value` from a resource."
  {:arglists '([url value])}
  (fn [_ value] (fhir-spec/fhir-type value)))

(defn- index-quantity-entries
  [{:keys [value system code unit]}]
  (let [system (type/value system)
        code (type/value code)
        unit (type/value unit)]
    (when-let [value (type/value value)]
      (cond-> [[nil (codec/quantity nil value)]]
        code
        (conj [nil (codec/quantity code value)])
        (and unit (not= unit code))
        (conj [nil (codec/quantity unit value)])
        (and system code)
        (conj [nil (codec/quantity (str system "|" code) value)])))))

(defmethod index-entries :fhir/Quantity
  [_ quantity]
  (index-quantity-entries quantity))

(defmethod index-entries :fhir/Age
  [_ quantity]
  (index-quantity-entries quantity))

(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "quantity")))

(defn- resource-value
  "Returns the value of the resource with `tid` and `id` according to the
  search parameter with `c-hash` starting with `prefix`.

  The `prefix` is important, because resources have more than one index entry
  and so more than one value per search parameter. Different unit
  representations and other possible prefixes from composite search parameters
  are responsible for the multiple values."
  {:arglists '([batch-db c-hash tid id prefix])}
  [{:keys [snapshot] :as batch-db} c-hash tid id prefix]
  (r-sp-v/next-value snapshot (p/-resource-handle batch-db tid id) c-hash
                     (bs/size prefix) prefix))

(defn- id-start-key [batch-db c-hash tid prefix start-id]
  (let [start-value (resource-value batch-db c-hash tid start-id prefix)]
    (assert start-value)
    (sp-vr/encode-seek-key c-hash tid start-value start-id)))

(defn- take-while-less-equal [c-hash tid value]
  (let [prefix-key (sp-vr/encode-seek-key c-hash tid value)]
    (take-while (fn [[prefix]] (bs/<= prefix prefix-key)))))

(def ^:private drop-value
  (map #(nth % 1)))

(defn- eq-handles
  "Returns a reducible collection of index handles of values between
  `lower-bound` and `upper-bound` starting at `start-id` (optional)."
  ([{:keys [snapshot]} c-hash tid lower-bound upper-bound]
   (coll/eduction
    (comp
     (take-while-less-equal c-hash tid upper-bound)
     drop-value
     u/by-id-grouper)
    (sp-vr/keys snapshot (sp-vr/encode-seek-key c-hash tid lower-bound))))
  ([{:keys [snapshot] :as batch-db} c-hash tid lower-bound-prefix upper-bound
    start-id]
   (coll/eduction
    (comp
     (take-while-less-equal c-hash tid upper-bound)
     drop-value
     u/by-id-grouper)
    (sp-vr/keys snapshot (id-start-key batch-db c-hash tid lower-bound-prefix
                                       start-id)))))

(defn- gt-handles
  "Returns a reducible collection of index handles of values greater
  than `value` starting at `start-id` (optional).

  The `prefix-length` is the length of the prefix of `value` that all found
  values have to have."
  ([{:keys [snapshot]} c-hash tid prefix-length value]
   (sp-vr/index-handles' snapshot c-hash tid prefix-length value))
  ([{:keys [snapshot] :as batch-db} c-hash tid prefix-length value start-id]
   (let [start-value (resource-value batch-db c-hash tid start-id
                                     (bs/subs value 0 prefix-length))]
     (assert start-value)
     (sp-vr/index-handles snapshot c-hash tid prefix-length start-value
                          start-id))))

(defn- lt-handles
  "Returns a reducible collection of index handles of values less than `value`
  starting at `start-id` (optional).

  The `prefix-length` is the length of the prefix of `value` that all found
  values have to have."
  ([{:keys [snapshot]} c-hash tid prefix-length value]
   (sp-vr/index-handles-prev' snapshot c-hash tid prefix-length value))
  ([{:keys [snapshot] :as batch-db} c-hash tid prefix-length value start-id]
   (let [start-value (resource-value batch-db c-hash tid start-id
                                     (bs/subs value 0 prefix-length))]
     (assert start-value)
     (sp-vr/index-handles-prev snapshot c-hash tid prefix-length start-value
                               start-id))))

(defn- ge-handles
  "Returns a reducible collection of index handles of values greater or equal
  `value` starting at `start-id` (optional).

  The `prefix-length` is the length of the prefix of `value` that all found
  values have to have."
  ([{:keys [snapshot]} c-hash tid prefix-length value]
   (sp-vr/index-handles snapshot c-hash tid prefix-length value))
  ([{:keys [snapshot] :as batch-db} c-hash tid prefix-length value start-id]
   (let [start-value (resource-value batch-db c-hash tid start-id
                                     (bs/subs value 0 prefix-length))]
     (assert start-value)
     (sp-vr/index-handles snapshot c-hash tid prefix-length start-value
                          start-id))))

(defn- le-handles
  "Returns a reducible collection of index handles of values less or equal
  `value` starting at `start-id` (optional).

  The `prefix-length` is the length of the prefix of `value` that all found
  values have to have."
  ([{:keys [snapshot]} c-hash tid prefix-length value]
   (sp-vr/index-handles-prev snapshot c-hash tid prefix-length value))
  ([{:keys [snapshot] :as batch-db} c-hash tid prefix-length value start-id]
   (let [start-value (resource-value batch-db c-hash tid start-id
                                     (bs/subs value 0 prefix-length))]
     (assert start-value)
     (sp-vr/index-handles-prev snapshot c-hash tid prefix-length start-value
                               start-id))))

(defn index-handles
  "Returns a reducible collection of index handles of values according to `op`
  and values starting at `start-id` (optional).

  The `prefix-length` is the length of the prefix of `value` that all found
  values have to have."
  {:arglists
   '([batch-db c-hash tid prefix-length value]
     [batch-db c-hash tid prefix-length value start-id])}
  ([batch-db c-hash tid prefix-length
    {:keys [op lower-bound exact-value upper-bound]}]
   (case op
     :eq (eq-handles batch-db c-hash tid lower-bound upper-bound)
     :gt (gt-handles batch-db c-hash tid prefix-length exact-value)
     :lt (lt-handles batch-db c-hash tid prefix-length exact-value)
     :ge (ge-handles batch-db c-hash tid prefix-length exact-value)
     :le (le-handles batch-db c-hash tid prefix-length exact-value)))
  ([batch-db c-hash tid prefix-length
    {:keys [op lower-bound exact-value upper-bound]}
    start-id]
   (case op
     :eq (eq-handles batch-db c-hash tid (bs/subs lower-bound 0 prefix-length)
                     upper-bound start-id)
     :gt (gt-handles batch-db c-hash tid prefix-length exact-value start-id)
     :lt (lt-handles batch-db c-hash tid prefix-length exact-value start-id)
     :ge (ge-handles batch-db c-hash tid prefix-length exact-value start-id)
     :le (le-handles batch-db c-hash tid prefix-length exact-value start-id))))

(defn- resource-handle-search-param-value-encoder [c-hash]
  (let [encoder (r-sp-v/resource-handle-search-param-value-encoder c-hash)]
    (fn [target-buf resource-handle {:keys [op] :as value}]
      (encoder target-buf resource-handle
               (value (if (identical? :eq op) :lower-bound :exact-value))))))

(defn matcher [{:keys [snapshot]} c-hash prefix-length values]
  (r-sp-v/value-filter
   snapshot
   (fn [{:keys [op]}]
     (case op (:lt :le) kv/seek-for-prev-buffer! kv/seek-buffer!))
   (resource-handle-search-param-value-encoder c-hash)
   (fn [value {:keys [op exact-value upper-bound]}]
     (case op
       :eq (bs/<= value upper-bound)
       :gt (bs/> value exact-value)
       :lt (bs/< value exact-value)
       true))
   (fn [resource-handle]
     (+ (r-sp-v/key-size (codec/id-byte-string (rh/id resource-handle)))
        (long prefix-length)))
   values))

(defn- single-version-id-search-param-value-encoder [tid c-hash]
  (let [encoder (r-sp-v/single-version-id-search-param-value-encoder tid c-hash)]
    (fn [target-buf single-version-id {:keys [op] :as value}]
      (encoder target-buf single-version-id
               (value (if (identical? :eq op) :lower-bound :exact-value))))))

(defn single-version-id-matcher [{:keys [snapshot]} tid c-hash prefix-length values]
  (r-sp-v/value-filter
   snapshot
   (fn [{:keys [op]}]
     (case op (:lt :le) kv/seek-for-prev-buffer! kv/seek-buffer!))
   (single-version-id-search-param-value-encoder tid c-hash)
   (fn [value {:keys [op exact-value upper-bound]}]
     (case op
       :eq (bs/<= value upper-bound)
       :gt (bs/> value exact-value)
       :lt (bs/< value exact-value)
       true))
   (fn [single-version-id]
     (+ (r-sp-v/key-size (svi/id single-version-id)) (long prefix-length)))
   values))

(defrecord SearchParamQuantity [name url type base code c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (let [[op value-and-unit] (u/separate-op value)
          [value unit] (str/split value-and-unit #"\s*\|\s*" 2)]
      (if-ok [decimal-value (system/parse-decimal value)]
        (case op
          :eq
          (u/eq-value (partial codec/quantity unit) decimal-value)
          (:gt :lt :ge :le)
          {:op op :exact-value (codec/quantity unit decimal-value)}
          (ba/unsupported
           (u/unsupported-prefix-msg code op)
           ::category ::unsupported-prefix
           ::unsupported-prefix op))
        #(assoc %
                ::category ::invalid-decimal-value
                ::anom/message (u/invalid-decimal-value-msg code value)))))

  (-estimated-scan-size [_ _ _ _ _]
    (ba/unsupported))

  (-index-handles [_ batch-db tid _ compiled-value]
    (index-handles batch-db c-hash tid codec/v-hash-size compiled-value))

  (-index-handles [_ batch-db tid _ compiled-value start-id]
    (index-handles batch-db c-hash tid codec/v-hash-size compiled-value
                   start-id))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db _ values]
    (matcher batch-db c-hash codec/v-hash-size values))

  (-single-version-id-matcher [_ batch-db tid _ compiled-values]
    (single-version-id-matcher batch-db tid c-hash codec/v-hash-size
                               compiled-values))

  (-second-pass-filter [_ _ _])

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defn- fix-expr
  "https://github.com/samply/blaze/issues/366"
  [url expression]
  (case url
    "http://hl7.org/fhir/SearchParameter/Observation-component-value-quantity"
    "Observation.component.value.ofType(Quantity)"
    "http://hl7.org/fhir/SearchParameter/Observation-combo-value-quantity"
    "(Observation.value as Quantity) | (Observation.value as SampledData) | Observation.component.value.ofType(Quantity) | Observation.component.value.ofType(SampledData)"
    expression))

(defmethod sc/search-param "quantity"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile (fix-expr url expression))]
      (->SearchParamQuantity name url type base code (codec/c-hash code)
                             expression))
    (ba/unsupported (u/missing-expression-msg url))))
