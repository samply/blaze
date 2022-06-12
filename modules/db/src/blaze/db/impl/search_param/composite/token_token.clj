(ns blaze.db.impl.search-param.composite.token-token
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.composite.common :as cc]
    [blaze.db.impl.search-param.token :as spt]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir-path :as fhir-path]))


(defrecord SearchParamCompositeTokenToken
  [name url type base code c-hash main-expression c1 c2]
  p/SearchParam
  (-compile-value [_ _modifier value]
    (let [[v1 v2] (cc/split-value value)
          v1 (cc/compile-component-value c1 v1)
          v2 (cc/compile-component-value c1 v2)]
      (bs/concat v1 v2)))

  (-resource-handles [_ context tid _ value]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (spt/resource-keys! context c-hash tid value)))

  (-resource-handles [_ context tid _ value start-did]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (spt/resource-keys! context c-hash tid value start-did)))

  (-matches? [_ context resource-handle _ values]
    (some? (some #(spt/matches? context c-hash resource-handle %) values)))

  (-index-values [_ resource-id resolver resource]
    (when-ok [values (fhir-path/eval resolver main-expression resource)]
      (coll/eduction (cc/index-values resource-id resolver c1 c2) values))))
