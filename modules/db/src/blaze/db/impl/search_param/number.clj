(ns blaze.db.impl.search-param.number
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
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

(defmulti index-entries
  "Returns index entries for `value` from a resource."
  {:arglists '([url value])}
  (fn [_ value] (fhir-spec/fhir-type value)))

(defmethod index-entries :fhir/decimal
  [_ value]
  [[nil (codec/number (type/value value))]])

(defn- encode-int [value]
  ;; TODO: we should not store the decimal form
  (codec/number (BigDecimal/valueOf ^long (type/value value))))

(defmethod index-entries :fhir/integer
  [_ value]
  [[nil (encode-int value)]])

(defmethod index-entries :fhir/unsignedInt
  [_ value]
  [[nil (encode-int value)]])

(defmethod index-entries :fhir/positiveInt
  [_ value]
  [[nil (encode-int value)]])

(defmethod index-entries :default
  [url value]
  (log/warn (u/format-skip-indexing-msg value url "number")))

(defrecord SearchParamNumber [name url type base code c-hash expression]
  p/SearchParam
  (-compile-value [_ _ value]
    (let [[op value] (u/separate-op value)]
      (if-ok [decimal-value (system/parse-decimal value)]
        (case op
          :eq
          (u/eq-value codec/number decimal-value)
          (:gt :lt :ge :le)
          {:op op :exact-value (codec/number decimal-value)}
          (ba/unsupported
           (u/unsupported-prefix-msg code op)
           ::category ::unsupported-prefix
           ::unsupported-prefix op))
        #(assoc %
                ::category ::invalid-decimal-value
                ::anom/message (u/invalid-decimal-value-msg code value)))))

  (-resource-handles [_ batch-db tid _ value]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (spq/resource-keys batch-db c-hash tid 0 value)))

  (-resource-handles [_ batch-db tid _ value start-id]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (spq/resource-keys batch-db c-hash tid 0 value start-id)))

  (-chunked-resource-handles [_ batch-db tid _ value]
    (coll/eduction
     (u/resource-handle-chunk-mapper batch-db tid)
     (spq/resource-keys batch-db c-hash tid 0 value)))

  (-matcher [_ batch-db _ values]
    (spq/matcher batch-db c-hash 0 values))

  (-index-values [search-param resolver resource]
    (when-ok [values (fhir-path/eval resolver expression resource)]
      (coll/eduction (p/-index-value-compiler search-param) values)))

  (-index-value-compiler [_]
    (mapcat (partial index-entries url))))

(defmethod sc/search-param "number"
  [_ {:keys [name url type base code expression]}]
  (if expression
    (when-ok [expression (fhir-path/compile expression)]
      (->SearchParamNumber name url type base code (codec/c-hash code)
                           expression))
    (ba/unsupported (u/missing-expression-msg url))))
