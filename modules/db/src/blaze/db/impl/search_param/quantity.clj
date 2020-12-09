(ns blaze.db.impl.search-param.quantity
  (:require
    [blaze.anomaly :refer [if-ok when-ok]]
    [blaze.byte-string :as bs]
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
    [blaze.fhir.spec.type.system :as system]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)



;; ---- compile-value ---------------------------------------------------------

(defn invalid-decimal-value-msg [code value]
  (format "Invalid decimal value `%s` in search parameter `%s`." value code))


(defn unsupported-prefix-msg [code op]
  (format "Unsupported prefix `%s` in search parameter `%s`." (name op) code))


(defn- eq-value [unit decimal-value]
  (let [delta (.movePointLeft 0.5M (.scale ^BigDecimal decimal-value))]
    {:op :eq
     :lower-bound (codec/quantity unit (- decimal-value delta))
     :exact-value (codec/quantity unit decimal-value)
     :upper-bound (codec/quantity unit (+ decimal-value delta))}))


(defn- compile-value [code value]
  (let [[op value-and-unit] (u/separate-op value)
        [value unit] (str/split value-and-unit #"\|" 2)]
    (if-ok [decimal-value (system/parse-decimal value)]
      (case op
        :eq
        (eq-value unit decimal-value)
        (:gt :lt :ge :le)
        {:op op :exact-value (codec/quantity unit decimal-value)}
        {::anom/category ::anom/unsupported
         ::category ::unsupported-prefix
         ::unsupported-prefix op
         ::anom/message (unsupported-prefix-msg code op)})
      (assoc decimal-value
        ::category ::invalid-decimal-value
        ::anom/message (invalid-decimal-value-msg code value)))))


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


(defn- resource-value
  "Returns the value of the resource with `tid` and `id` according to the
  search parameter with `c-hash` starting with `prefix`.

  The `prefix` is important, because resources have more than one index entry
  and so more than one value per search parameter. Different unit
  representations and other possible prefixes from composite search parameters
  are responsible for the multiple values."
  [{:keys [rsvi resource-handle]} c-hash tid id prefix]
  (let [handle (resource-handle tid id)]
    (r-sp-v/next-value! rsvi handle c-hash prefix prefix)))


(defn- id-start-key [context c-hash tid prefix start-id]
  (let [start-value (resource-value context c-hash tid start-id prefix)]
    (assert start-value)
    (sp-vr/encode-seek-key c-hash tid start-value start-id)))


(defn- take-while-less-equal [c-hash tid value]
  (let [prefix-key (sp-vr/encode-seek-key c-hash tid value)]
    (take-while (fn [[prefix]] (bs/<= prefix prefix-key)))))


(defn- eq-keys!
  "Returns a reducible collection of `[id hash-prefix]` tuples of values between
  `lower-bound` and `upper-bound` starting at `start-id` (optional).

  The `prefix` is a fix prefix of `value` which all found values have to have.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([{:keys [svri]} c-hash tid lower-bound upper-bound]
   (coll/eduction
     (comp
       (take-while-less-equal c-hash tid upper-bound)
       (map (fn [[_ id hash-prefix]] [id hash-prefix])))
     (sp-vr/keys! svri (sp-vr/encode-seek-key c-hash tid lower-bound))))
  ([{:keys [svri] :as context} c-hash tid lower-bound-prefix upper-bound
    start-id]
   (coll/eduction
     (comp
       (take-while-less-equal c-hash tid upper-bound)
       (map (fn [[_ id hash-prefix]] [id hash-prefix])))
     (sp-vr/keys! svri (id-start-key context c-hash tid lower-bound-prefix
                                     start-id)))))


(defn- gt-keys!
  "Returns a reducible collection of `[id hash-prefix]` tuples of values greater
  than `value` starting at `start-id` (optional).

  The `prefix` is a fix prefix of `value` which all found values have to have.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([{:keys [svri]} c-hash tid prefix value]
   (sp-vr/prefix-keys'! svri c-hash tid prefix value))
  ([{:keys [svri] :as context} c-hash tid prefix _value start-id]
   (let [start-value (resource-value context c-hash tid start-id prefix)]
     (assert start-value)
     (sp-vr/prefix-keys! svri c-hash tid prefix start-value start-id))))


(defn- lt-keys!
  "Returns a reducible collection of `[id hash-prefix]` tuples of values less
  than `value` starting at `start-id` (optional).

  The `prefix` is a fix prefix of `value` which all found values have to have.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([{:keys [svri]} c-hash tid prefix value]
   (sp-vr/prefix-keys-prev'! svri c-hash tid prefix value))
  ([{:keys [svri] :as context} c-hash tid prefix _value start-id]
   (let [start-value (resource-value context c-hash tid start-id prefix)]
     (assert start-value)
     (sp-vr/prefix-keys-prev! svri c-hash tid prefix start-value start-id))))


(defn- ge-keys!
  "Returns a reducible collection of `[id hash-prefix]` tuples of values greater
  or equal `value` starting at `start-id` (optional).

  The `prefix` is a fix prefix of `value` which all found values have to have.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([{:keys [svri]} c-hash tid prefix value]
   (sp-vr/prefix-keys! svri c-hash tid prefix value))
  ([{:keys [svri] :as context} c-hash tid prefix _value start-id]
   (let [start-value (resource-value context c-hash tid start-id prefix)]
     (assert start-value)
     (sp-vr/prefix-keys! svri c-hash tid prefix start-value start-id))))


(defn- le-keys!
  "Returns a reducible collection of `[id hash-prefix]` tuples of values less
  or equal `value` starting at `start-id` (optional).

  The `prefix` is a fix prefix of `value` which all found values have to have.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([{:keys [svri]} c-hash tid prefix value]
   (sp-vr/prefix-keys-prev! svri c-hash tid prefix value))
  ([{:keys [svri] :as context} c-hash tid prefix _value start-id]
   (let [start-value (resource-value context c-hash tid start-id prefix)]
     (assert start-value)
     (sp-vr/prefix-keys-prev! svri c-hash tid prefix start-value start-id))))


(defn resource-keys!
  "Returns a reducible collection of `[id hash-prefix]` tuples of values
  according to `op` and values starting at `start-id` (optional).

  The `prefix-length` is the length of the fix prefix which all found values
  have to have.

  Changes the state of `iter`. Consuming the collection requires exclusive
  access to `iter`. Doesn't close `iter`."
  ([context c-hash tid prefix-length
   {:keys [op lower-bound exact-value upper-bound]}]
   (case op
     :eq (eq-keys! context c-hash tid lower-bound upper-bound)
     :gt (gt-keys! context c-hash tid (bs/subs exact-value 0 prefix-length)
                   exact-value)
     :lt (lt-keys! context c-hash tid (bs/subs exact-value 0 prefix-length)
                   exact-value)
     :ge (ge-keys! context c-hash tid (bs/subs exact-value 0 prefix-length)
                   exact-value)
     :le (le-keys! context c-hash tid (bs/subs exact-value 0 prefix-length)
                   exact-value)))
  ([context c-hash tid prefix-length
   {:keys [op lower-bound exact-value upper-bound]}
   start-id]
   (case op
     :eq (eq-keys! context c-hash tid (bs/subs lower-bound 0 prefix-length)
                   upper-bound start-id)
     :gt (gt-keys! context c-hash tid (bs/subs exact-value 0 prefix-length)
                   exact-value start-id)
     :lt (lt-keys! context c-hash tid (bs/subs exact-value 0 prefix-length)
                   exact-value start-id)
     :ge (ge-keys! context c-hash tid (bs/subs exact-value 0 prefix-length)
                   exact-value start-id)
     :le (le-keys! context c-hash tid (bs/subs exact-value 0 prefix-length)
                   exact-value start-id))))


(defn- take-while-compartment-less-equal [compartment c-hash tid value]
  (let [prefix-key (c-sp-vr/encode-seek-key compartment c-hash tid value)]
    (take-while (fn [[prefix]] (bs/<= prefix prefix-key)))))


(defn- eq-compartment-keys
  [{:keys [csvri]} compartment c-hash tid lower-bound upper-bound]
  (coll/eduction
    (comp
      (take-while-compartment-less-equal compartment c-hash tid upper-bound)
      (map (fn [[_ id hash-prefix]] [id hash-prefix])))
    (c-sp-vr/keys! csvri (c-sp-vr/encode-seek-key compartment c-hash tid
                                                  lower-bound))))

(defn- compartment-keys
  [context compartment c-hash tid {:keys [op lower-bound upper-bound]}]
  (case op
    :eq (eq-compartment-keys context compartment c-hash tid lower-bound
                             upper-bound)))


(defn eq-matches?
  [{:keys [rsvi]} c-hash resource-handle prefix lower-bound upper-bound]
  (when-let [value (r-sp-v/next-value! rsvi resource-handle c-hash prefix lower-bound)]
    (bs/<= value upper-bound)))


(defn gt-matches? [{:keys [rsvi]} c-hash resource-handle prefix value]
  (when-let [found-value (r-sp-v/next-value! rsvi resource-handle c-hash prefix value)]
    (bs/> found-value value)))


(defn lt-matches? [{:keys [rsvi]} c-hash resource-handle prefix value]
  (when-let [found-value (r-sp-v/next-value-prev! rsvi resource-handle c-hash prefix value)]
    (bs/< found-value value)))


(defn ge-matches? [{:keys [rsvi]} c-hash resource-handle prefix value]
  (some? (r-sp-v/next-value! rsvi resource-handle c-hash prefix value)))


(defn le-matches? [{:keys [rsvi]} c-hash resource-handle prefix value]
  (some? (r-sp-v/next-value-prev! rsvi resource-handle c-hash prefix value)))


(defn matches?
  [context c-hash resource-handle prefix-length
   {:keys [op lower-bound exact-value upper-bound]}]
  (case op
    :eq (eq-matches? context c-hash resource-handle
                     (bs/subs lower-bound 0 prefix-length) lower-bound
                     upper-bound)
    :gt (gt-matches? context c-hash resource-handle
                     (bs/subs exact-value 0 prefix-length) exact-value)
    :lt (lt-matches? context c-hash resource-handle
                     (bs/subs exact-value 0 prefix-length) exact-value)
    :ge (ge-matches? context c-hash resource-handle
                     (bs/subs exact-value 0 prefix-length) exact-value)
    :le (le-matches? context c-hash resource-handle
                     (bs/subs exact-value 0 prefix-length) exact-value)))


(defrecord SearchParamQuantity [name url type base code c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (compile-value code value))

  (-resource-handles [_ context tid _ value]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys! context c-hash tid codec/v-hash-size value)))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (resource-keys! context c-hash tid codec/v-hash-size value start-id)))

  (-compartment-keys [_ context compartment tid value]
    (compartment-keys context compartment c-hash tid value))

  (-matches? [_ context resource-handle _ values]
    (some #(matches? context c-hash resource-handle codec/v-hash-size %) values))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (p/-compile-index-values search-param values)))

  (-compile-index-values [_ values]
    (into
      []
      (mapcat
        #(quantity-index-entries
           url
           (fn [unit value]
             [[nil (codec/quantity unit value)]])
           %))
      values)))


(defmethod sr/search-param "quantity"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamQuantity name url type base code (codec/c-hash code)
                             expression))
    {::anom/category ::anom/unsupported
     ::anom/message (u/missing-expression-msg url)}))
