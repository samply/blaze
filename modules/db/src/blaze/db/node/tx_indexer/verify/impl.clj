(ns blaze.db.node.tx-indexer.verify.impl
  (:require
   [blaze.db.node.stats :as node-stats]
   [blaze.db.search-param-registry :as sr]))

(defn- non-empty-type? [stats type]
  (pos? (long (node-stats/type-total stats type))))

(defn- source-type-code-tuples [{:keys [base code]}]
  (map #(vector % code) base))

(defn rev-include-params
  "Returns tuples of source type and code of the reference search params of
  `search-param-registry` pointing to resources of `type`, skipping source
  types without resources according to `stats`.

  A source type without resources can't hold a reference, so seeking the
  references of its search params would only cost time. Because the base types
  of most search params have no resources in common datasets, skipping them
  saves most of the seeks.

  The totals are taken from the in-memory `stats` (see `blaze.db.node.stats`)
  instead of the TypeStats index, because one is needed per source type and
  code, all on the single indexing thread."
  [search-param-registry stats type]
  (into
   []
   (comp
    (mapcat source-type-code-tuples)
    (filter (comp (partial non-empty-type? stats) first)))
   (sr/list-by-target-type search-param-registry type)))
