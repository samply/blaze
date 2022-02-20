(ns blaze.db.impl.search-param.number
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.anomaly-spec]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.core :as sc]
    [blaze.db.impl.search-param.quantity :as spq]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)



;; ---- compile-value ---------------------------------------------------------

(defn invalid-decimal-value-msg [code value]
  (format "Invalid decimal value `%s` in search parameter `%s`." value code))


(defn unsupported-prefix-msg [code op]
  (format "Unsupported prefix `%s` in search parameter `%s`." (name op) code))


(defn- eq-value [decimal-value]
  (let [delta (.movePointLeft 0.5M (.scale ^BigDecimal decimal-value))]
    {:op :eq
     :lower-bound (codec/number (- decimal-value delta))
     :exact-value (codec/number decimal-value)
     :upper-bound (codec/number (+ decimal-value delta))}))


(defmulti index-entries
  "Returns index entries for `value` from a resource."
  {:arglists '([url value])}
  (fn [_ value] (fhir-spec/fhir-type value)))


(defmethod index-entries :fhir/decimal
  [_ value]
  [[nil (codec/number (type/value value))]])


(defmethod index-entries :fhir/integer
  [_ value]
  ;; TODO: we should not store the decimal form
  [[nil (codec/number (BigDecimal/valueOf ^long (type/value value)))]])


(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "number")))


(defrecord SearchParamQuantity [name url type base code c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (let [[op value] (u/separate-op value)]
      (if-ok [decimal-value (system/parse-decimal value)]
        (case op
          :eq
          (eq-value decimal-value)
          (:gt :lt :ge :le)
          {:op op :exact-value (codec/number decimal-value)}
          (ba/unsupported
            (unsupported-prefix-msg code op)
            ::category ::unsupported-prefix
            ::unsupported-prefix op))
        #(assoc %
           ::category ::invalid-decimal-value
           ::anom/message (invalid-decimal-value-msg code value)))))

  (-resource-handles [_ context tid _ value]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (spq/resource-keys! context c-hash tid 0 value)))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (spq/resource-keys! context c-hash tid 0 value start-id)))

  (-matches? [_ context resource-handle _ values]
    (some? (some #(spq/matches? context c-hash resource-handle 0 %) values)))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))


(defmethod sc/search-param "number"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamQuantity name url type base code (codec/c-hash code)
                             expression))
    (ba/unsupported (u/missing-expression-msg url))))
