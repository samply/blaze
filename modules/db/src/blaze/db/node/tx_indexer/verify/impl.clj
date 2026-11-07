(ns blaze.db.node.tx-indexer.verify.impl
  (:require
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.node.stats :as node-stats]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.search-param-registry :as sr]))

(defn- rev-include-param-tuples [{:keys [base code]}]
  (map #(tx-u/rev-include-param % code) base))

(defn- references-type?
  "Returns true if the search param of `rev-include-param` holds at least one
  reference to a resource with `tid` in `snapshot`.

  The references are found by the SearchParamValueResource keys whose value
  starts with `tid`, the way `rev-include` seeks them."
  {:arglists '([snapshot tid rev-include-param])}
  [snapshot tid [_ source-tid c-hash]]
  (some? (coll/first (sp-vr/index-handles snapshot c-hash source-tid
                                          codec/tid-size
                                          (codec/tid-byte-string tid)))))

(defn rev-include-params
  "Returns tuples of source type, source tid and c-hash of the code of the
  reference search params pointing to resources of `type` in `db` (see
  `tx-u/rev-include-param`), skipping
  source types without resources according to `stats` and, if
  `multiple-deletes?`, so more than one resource of `type` is deleted, search
  params without any reference to a resource of `type`.

  The `stats` have to be the ones of `db`.

  The tids and c-hashes are computed here once per type, because the params
  are used for every deleted resource of that type.

  A source type without resources can't hold a reference, so seeking the
  references of its search params would only cost time. Because the base types
  of most search params have no resources in common datasets, skipping them
  saves most of the seeks.

  The totals are taken from the in-memory `stats` (see `blaze.db.node.stats`)
  instead of the TypeStats index, because one is needed per source type and
  code, all on the single indexing thread.

  A search param of a source type with resources still often holds no
  reference to a resource of `type`, because it's never used or points only to
  resources of other types. One seek per search param finds out, which saves
  the seek of that search param for every deleted resource of `type`. Index
  entries of versions no longer current can make a search param look like it
  holds a reference, which only costs the seeks saved otherwise.

  A single deleted resource takes one seek per search param anyway, so finding
  out first would only add the seeks of the search params holding references.
  That's why the search params are only checked for references if more than
  one resource of `type` is deleted."
  {:arglists '([db stats type multiple-deletes?])}
  [{{:keys [search-param-registry]} :node :keys [snapshot]} stats type
   multiple-deletes?]
  (into
   []
   (cond->
    (comp
     (mapcat rev-include-param-tuples)
     (filter (comp (partial node-stats/non-empty-type? stats) first)))
     multiple-deletes?
     (comp (filter (partial references-type? snapshot (codec/tid type)))))
   (sr/list-by-target-type search-param-registry type)))

(defn- clause-has-index-entry?
  "Returns true if the SearchParamValueResource index of `db` holds at least
  one entry of a resource with `tid` matching `clause`."
  {:arglists '([db tid clause])}
  [db tid [search-param modifier _ compiled-values]]
  (some? (coll/first (search-param/index-handles search-param db tid modifier
                                                 compiled-values))))

(defn type-may-match?
  "Returns true if a resource of `type` in `db` may match `matcher`.

  Returns false only if a disjunction of the clauses of `matcher` has no index
  entry of any version of a resource of `type`, so that no current version can
  match either. Takes one seek per clause instead of one per resource, as
  `blaze.db.api/matches?` would. Index entries of versions no longer current
  can make a type look like it may match, which only costs the seeks saved
  otherwise."
  {:arglists '([db matcher type])}
  [db {:keys [search-clauses]} type]
  (let [tid (codec/tid type)]
    (every?
     #(boolean (some (partial clause-has-index-entry? db tid) %))
     search-clauses)))

(def ^:private ^:const ^long delete-shard-bits
  "The number of bits of the hash of an id choosing its shard (see
  `delete-shard`)."
  5)

(def ^:const ^long num-delete-shards
  "The number of shards the deleted resources of a type are split into by the
  hash of their id, so that the deleted resources of the parts of a large
  transaction can be merged in parallel, one task per shard."
  (bit-shift-left 1 delete-shard-bits))

(def ^:private empty-delete-shards
  (vec (repeat num-delete-shards {})))

(defn- delete-shard
  "Returns the index of the shard holding `id` in the shards of the deleted
  resources of a type.

  Takes the top bits of the hash of `id`, because the hash maps of the shards
  use its bottom bits first, so that the ids of a shard still spread over the
  nodes of its map."
  ^long [id]
  (bit-and (bit-shift-right (hash id) (- 32 delete-shard-bits))
           (dec num-delete-shards)))

(defn assoc-delete
  "Returns `shards`, the shards of the deleted resources of a type, or new
  empty shards if nil, with `id` mapped to `value`."
  [shards id value]
  (update (or shards empty-delete-shards) (delete-shard id) assoc id value))

(defn delete-value
  "Returns the value of `id` in `shards`, the shards of the deleted resources
  of a type (see `assoc-delete`), or nil if it isn't in them."
  [shards id]
  (get (shards (delete-shard id)) id))

(defn- deleted-id?
  "Returns true if `shards`, the shards of the deleted resources of a type (see
  `assoc-delete`), hold `id`."
  [shards id]
  (contains? (shards (delete-shard id)) id))

(defn- into-larger
  "Returns the union of `m1` and `m2`, maps from the ids of deleted resources,
  by adding the smaller map to the larger one. The maps have no ids in common,
  because a transaction deletes every resource at most once, so that no entry
  replaces another."
  [m1 m2]
  (if (< (count m1) (count m2)) (into m2 m1) (into m1 m2)))

(defn merge-delete-shard
  "Returns the shard with `index` merged from `part-shards`, the shards of the
  deleted resources of a type of several parts of a transaction (see
  `assoc-delete`)."
  [part-shards index]
  (transduce (map #(% index)) (completing into-larger) {} part-shards))

(defn- deleted-xf
  "Returns a transducer removing the index handles whose ids are in `shards`,
  the shards of the deleted resources of a type (see `assoc-delete`)."
  [shards]
  (remove #(deleted-id? shards (ih/id %))))

(defn rev-include
  "Returns a reducible collection of the resource handles of type
  `source-type` that point to `reference` by the search param with `c-hash`
  and aren't in `deletes`, a map from the type of the resources deleted by the
  transaction or whose deletes check references to the shards of their ids
  (see `assoc-delete`).

  The shards of `source-type` are looked up once, so that each index handle
  only needs a lookup of its id and no tuple of type and id.

  The `reference` is the tid-id of the resource handle pointed to, and the
  tuple of `source-type`, `source-tid` and `c-hash` is one of the
  `rev-include-params`.

  The resources in `deletes` are skipped before their resource handles are
  resolved, because the index entries already hold their ids. That saves most
  lookups when a Patient is purged, because nearly all resources pointing to
  the resources of its compartment are purged along with them.

  Resources deleted before aren't returned either, because they don't
  reference anything any more. A resource handle is only resolved for an index
  entry if its hash prefix matches one of the entry. Index entries are written
  for content versions only, but an entry of an earlier version can still match
  the deleted version by a collision of their 4-byte hash prefixes. So the
  `deletes` can also hold resources already deleted before, which they do if
  their deletes check references."
  {:arglists '([db reference [source-type source-tid c-hash] deletes])}
  [{:keys [snapshot] :as db} reference [source-type source-tid c-hash]
   deletes]
  (let [shards (get deletes source-type)]
    (coll/eduction
     (cond->> (comp (u/resource-handle-xf db source-tid) (remove rh/deleted?))
       shards (comp (deleted-xf shards)))
     (sp-vr/index-handles-full-value snapshot c-hash source-tid reference))))
