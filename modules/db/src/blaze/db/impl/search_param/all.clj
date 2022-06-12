(ns blaze.db.impl.search-param.all
  "A internal search parameter returning all resources of a type.

  This search param is used to put the date search param on _lastUpdated into
  second position if no other search param is available for the first position.

  The date search param on _lastUpdated can't be in first position, because it
  will return resources more than once if multiple updates with the same hash
  are index with different lastUpdate times."
  (:require
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.db.impl.protocols :as p]))


(def search-param
  (reify p/SearchParam
    (-compile-value [_ _ _])

    (-resource-handles [_ context tid _ _]
      (rao/type-list context tid))

    (-resource-handles [_ context tid _ _ start-did]
      (rao/type-list context tid start-did))

    (-index-values [_ _ _ _])))
