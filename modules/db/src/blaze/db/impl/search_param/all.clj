(ns blaze.db.impl.search-param.all
  "A internal search parameter returning all resources of a type.

  This search param is used to put the date search param on _lastUpdated into
  second position if no other search param is available for the first position.

  The date search param on _lastUpdated can't be in first position, because it
  will return resources more than once if multiple updates with the same hash
  are index with different lastUpdate times."
  (:require
   [blaze.anomaly :as ba]
   [blaze.coll.core :as coll]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.protocols :as p]))

(def search-param
  (reify p/SearchParam
    (-compile-value [_ _ _])

    (-estimated-storage-size [_ _ _ _ _]
      (ba/unsupported))

    (-index-handles [_ batch-db tid _ _]
      (coll/eduction
       (map ih/from-resource-handle)
       (rao/type-list batch-db tid)))

    (-index-handles [_ batch-db tid _ _ start-id]
      (coll/eduction
       (map ih/from-resource-handle)
       (rao/type-list batch-db tid start-id)))

    (-supports-ordered-index-handles [_]
      true)

    (-ordered-index-handles [search-param batch-db tid modifier compiled-value]
      (p/-index-handles search-param batch-db tid modifier compiled-value))

    (-ordered-index-handles [search-param batch-db tid modifier compiled-value start-id]
      (p/-index-handles search-param batch-db tid modifier compiled-value start-id))

    (-supports-ordered-compartment-index-handles [_ _]
      false)

    (-second-pass-filter [_ _ _])

    (-index-values [_ _ _])))
