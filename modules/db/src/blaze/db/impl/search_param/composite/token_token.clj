(ns blaze.db.impl.search-param.composite.token-token
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.composite.common :as cc]
   [blaze.db.impl.search-param.token :as spt]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]))

(defrecord SearchParamCompositeTokenToken
           [name url type base code c-hash main-expression c1 c2]
  p/SearchParam
  (-compile-value [_ _ value]
    (when-ok [[v1 v2] (cc/split-value value)]
      (let [v1 (cc/compile-component-value c1 v1)
            v2 (cc/compile-component-value c1 v2)]
        (bs/concat v1 v2))))

  (-chunked-resource-handles [_ context tid _ value]
    (coll/eduction
     (u/resource-handle-chunk-mapper context tid)
     (spt/resource-keys context c-hash tid value)))

  (-resource-handles [_ context tid _ value]
    (coll/eduction
     (u/resource-handle-mapper context tid)
     (spt/resource-keys context c-hash tid value)))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
     (u/resource-handle-mapper context tid)
     (spt/resource-keys context c-hash tid value start-id)))

  (-matcher [_ context _ values]
    (r-sp-v/value-prefix-filter (:snapshot context) c-hash values))

  (-index-values [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver main-expression resource)]
      (coll/eduction (cc/index-values resolver c1 c2) values))))
