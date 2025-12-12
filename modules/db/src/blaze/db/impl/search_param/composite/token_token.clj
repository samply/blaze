(ns blaze.db.impl.search-param.composite.token-token
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.composite.common :as cc]
   [blaze.db.impl.search-param.token :as spt]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]))

(defrecord SearchParamCompositeTokenToken [name url type base code c-hash
                                           main-expression c1 c2]
  p/SearchParam
  (-validate-modifier [_ modifier]
    (some->> modifier (u/unknown-modifier-anom code)))

  (-compile-value [_ _ value]
    (when-ok [[v1 v2] (cc/split-value value)]
      (let [v1 (cc/compile-component-value c1 v1)
            v2 (cc/compile-component-value c1 v2)]
        (bs/concat v1 v2))))

  (-estimated-scan-size [_ batch-db tid _ compiled-value]
    (sp-vr/estimated-scan-size (:kv-store batch-db) c-hash tid compiled-value))

  (-supports-ordered-index-handles [_ _ _ _ _]
    true)

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values]
    (if (= 1 (count compiled-values))
      (p/-index-handles search-param batch-db tid modifier (first compiled-values))
      (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
        (u/union-index-handles (map index-handles compiled-values)))))

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values start-id]
    (if (= 1 (count compiled-values))
      (p/-index-handles search-param batch-db tid modifier (first compiled-values) start-id)
      (let [index-handles #(p/-index-handles search-param batch-db tid modifier % start-id)]
        (u/union-index-handles (map index-handles compiled-values)))))

  (-index-handles [_ batch-db tid _ compiled-value]
    (spt/index-handles batch-db c-hash tid compiled-value))

  (-index-handles [_ batch-db tid _ compiled-value start-id]
    (spt/index-handles batch-db c-hash tid compiled-value start-id))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db _ compiled-values]
    (r-sp-v/value-prefix-filter (:snapshot batch-db) c-hash compiled-values))

  (-postprocess-matches [_ _ _ _])

  (-index-values [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver main-expression resource)]
      (coll/eduction (cc/index-values resolver c1 c2) values))))
