(ns blaze.db.node.tx-indexer.verify.impl
  (:require
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.node.stats :as node-stats]
   [blaze.db.search-param-registry :as sr]))

(defn- non-empty-type? [stats type]
  (pos? (long (node-stats/type-total stats type))))

(defn- rev-include-param-tuples [{:keys [base code]}]
  (let [c-hash (codec/c-hash code)]
    (map #(vector % (codec/tid %) c-hash) base)))

(defn rev-include-params
  "Returns tuples of source type, source tid and c-hash of the code of the
  reference search params of `search-param-registry` pointing to resources of
  `type`, skipping source types without resources according to `stats`.

  The tids and c-hashes are computed here once per type, because the params
  are used for every deleted resource of that type.

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
    (mapcat rev-include-param-tuples)
    (filter (comp (partial non-empty-type? stats) first)))
   (sr/list-by-target-type search-param-registry type)))

(defn- deleted-xf [source-type del-resources]
  (remove #(contains? del-resources [source-type (ih/id %)])))

(defn rev-include
  "Returns a reducible collection of the resource handles of type
  `source-type` that point to `reference` by the search param with `c-hash`
  and aren't in `del-resources`.

  The `reference` is the tid-id of the resource handle pointed to, and the
  tuple of `source-type`, `source-tid` and `c-hash` is one of the
  `rev-include-params`.

  The resources in `del-resources` are skipped before their resource handles
  are resolved, because the index entries already hold their ids. That saves
  most lookups when a Patient is purged, because nearly all resources pointing
  to the resources of its compartment are purged along with them."
  {:arglists '([db reference [source-type source-tid c-hash] del-resources])}
  [{:keys [snapshot] :as db} reference [source-type source-tid c-hash]
   del-resources]
  (coll/eduction
   (comp
    (deleted-xf source-type del-resources)
    (u/resource-handle-xf db source-tid))
   (sp-vr/index-handles-full-value snapshot c-hash source-tid reference)))
