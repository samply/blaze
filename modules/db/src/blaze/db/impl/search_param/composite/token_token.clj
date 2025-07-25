(ns blaze.db.impl.search-param.composite.token-token
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.composite.common :as cc]
   [blaze.db.impl.search-param.token :as spt]
   [blaze.fhir-path :as fhir-path]))

(defrecord SearchParamCompositeTokenToken
           [name url type base code c-hash main-expression c1 c2]
  p/SearchParam
  (-compile-value [_ _ value]
    (when-ok [[v1 v2] (cc/split-value value)]
      (let [v1 (cc/compile-component-value c1 v1)
            v2 (cc/compile-component-value c1 v2)]
        (bs/concat v1 v2))))

  (-index-handles [_ batch-db tid _ compiled-value]
    (spt/index-handles batch-db c-hash tid compiled-value))

  (-index-handles [_ batch-db tid _ compiled-value start-id]
    (spt/index-handles batch-db c-hash tid compiled-value start-id))

  (-supports-ordered-index-handles [_]
    true)

  (-ordered-index-handles [search-param batch-db tid modifier compiled-value]
    (p/-index-handles search-param batch-db tid modifier compiled-value))

  (-ordered-index-handles [search-param batch-db tid modifier compiled-value start-id]
    (p/-index-handles search-param batch-db tid modifier compiled-value start-id))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-matcher [_ batch-db _ compiled-values]
    (r-sp-v/value-prefix-filter (:snapshot batch-db) c-hash compiled-values))

  (-second-pass-filter [_ _ _])

  (-index-values [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver main-expression resource)]
      (coll/eduction (cc/index-values resolver c1 c2) values))))
