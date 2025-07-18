(ns blaze.db.impl.search-param.chained
  (:require
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.node.resource-indexer.spec]
   [blaze.db.node.spec]
   [blaze.db.search-param-registry.spec]))

(defn targets
  "Returns a reducible collection of non-deleted resource handles that are
  referenced by `resource-handle` via a search-param with `code` having a type
  with `target-tid` (optional)."
  {:arglists
   '([batch-db resource-handle code]
     [batch-db resource-handle code target-tid])}
  ([{:keys [snapshot] :as batch-db} {:keys [tid id hash]} code]
   (coll/eduction
    (u/reference-resource-handle-mapper batch-db)
    (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash code)))
  ([{:keys [snapshot] :as batch-db} {:keys [tid id hash]} code target-tid]
   (coll/eduction
    (u/reference-resource-handle-mapper batch-db)
    (let [start-value (codec/tid-byte-string target-tid)]
      (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash code
                          (bs/size start-value) start-value)))))

(defrecord ChainedSearchParam [search-param ref-search-param ref-c-hash ref-tid ref-modifier code]
  p/SearchParam
  (-compile-value [_ modifier value]
    (p/-compile-value search-param modifier value))

  (-resource-handles [_ batch-db tid modifier compiled-value]
    (coll/eduction
     (comp (map #(p/-compile-value ref-search-param ref-modifier (rh/reference %)))
           (mapcat #(p/-resource-handles ref-search-param batch-db tid modifier %))
           (distinct))
     (p/-resource-handles search-param batch-db ref-tid modifier compiled-value)))

  (-resource-handles [this batch-db tid modifier compiled-value start-id]
    (let [start-id (codec/id-string start-id)]
      (coll/eduction
       (drop-while #(not= start-id (rh/id %)))
       (p/-resource-handles this batch-db tid modifier compiled-value))))

  (-chunked-resource-handles [this batch-db tid modifier value]
    [(p/-resource-handles this batch-db tid modifier value)])

  (-matcher [_ batch-db modifier values]
    (filter
     (fn [resource-handle]
       (transduce
        (p/-matcher search-param batch-db modifier values)
        (fn ([r] r) ([_ _] (reduced true)))
        nil
        (targets batch-db resource-handle ref-c-hash ref-tid))))))

(defn chained-search-param
  [search-param ref-search-param ref-type ref-modifier original-code modifier]
  [(->ChainedSearchParam search-param ref-search-param
                         (codec/c-hash (:code ref-search-param))
                         (codec/tid ref-type) ref-modifier original-code)
   modifier])
