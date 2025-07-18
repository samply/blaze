(ns blaze.db.impl.search-param.composite.token-quantity
  (:require
   [blaze.anomaly :refer [if-ok when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.composite.common :as cc]
   [blaze.db.impl.search-param.quantity :as spq]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]
   [cognitect.anomalies :as anom]))

(set! *warn-on-reflection* true)

(defn- prefix-with* [quantity-value token-value]
  (bs/concat token-value quantity-value))

(defn- prefix-with [{:keys [op] :as quantity-value} token-value]
  (if (identical? :eq op)
    (-> (update quantity-value :lower-bound prefix-with* token-value)
        (update :upper-bound prefix-with* token-value))
    (update quantity-value :exact-value prefix-with* token-value)))

(def ^:private ^:const ^long prefix-length
  "Length of the prefix while scanning over indices consists of the token and
  the unit of the quantity."
  (* 2 codec/v-hash-size))

(defrecord SearchParamCompositeTokenQuantity [name url type base code c-hash main-expression c1 c2]
  p/SearchParam
  (-compile-value [_ _ value]
    (when-ok [[v1 v2] (cc/split-value value)]
      (let [token-value (cc/compile-component-value c1 v1)]
        (if-ok [quantity-value (cc/compile-component-value c2 v2)]
          (prefix-with quantity-value token-value)
          #(case (::spq/category %)
             ::spq/invalid-decimal-value
             (assoc %
                    ::anom/message (u/invalid-decimal-value-msg code v2))
             ::spq/unsupported-prefix
             (assoc %
                    ::anom/message
                    (u/unsupported-prefix-msg
                     code (::spq/unsupported-prefix %)))
             %)))))

  (-resource-handles [_ batch-db tid _ value]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (spq/resource-keys batch-db c-hash tid prefix-length value)))

  (-resource-handles [_ batch-db tid _ value start-id]
    (coll/eduction
     (u/resource-handle-mapper batch-db tid)
     (spq/resource-keys batch-db c-hash tid prefix-length value start-id)))

  (-chunked-resource-handles [_ batch-db tid _ value]
    (coll/eduction
     (u/resource-handle-chunk-mapper batch-db tid)
     (spq/resource-keys batch-db c-hash tid prefix-length value)))

  (-matcher [_ batch-db _ values]
    (spq/matcher batch-db c-hash prefix-length values))

  (-index-values [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver main-expression resource)]
      (coll/eduction (cc/index-values resolver c1 c2) values))))
