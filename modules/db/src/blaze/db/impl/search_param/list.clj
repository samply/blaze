(ns blaze.db.impl.search-param.list
  "https://www.hl7.org/fhir/search.html#list"
  (:require
   [blaze.anomaly :as ba]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.special :as special]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir.spec]))

(set! *warn-on-reflection* true)

(def ^:private list-tid (codec/tid "List"))
(def ^:private item-c-hash (codec/c-hash "item"))

(defn- list-handle [batch-db list-id]
  (u/non-deleted-resource-handle batch-db list-tid list-id))

(defn- list-hash [batch-db list-id]
  (some-> (list-handle batch-db list-id) rh/hash))

(defn- referenced-index-handles
  "Returns a reducible collection of index handles of type `tid` that are
  referenced by the list with `list-id` and `list-hash`, starting with
  `start-id` (optional)."
  {:arglists
   '([batch-db list-id list-hash tid]
     [batch-db list-id list-hash tid start-id])}
  ([{:keys [snapshot] :as batch-db} list-id list-hash tid]
   (coll/eduction
    (comp (u/reference-resource-handle-mapper batch-db)
          (map ih/from-resource-handle))
    (r-sp-v/prefix-keys snapshot list-tid list-id list-hash item-c-hash
                        codec/tid-size (codec/tid-byte-string tid))))
  ([{:keys [snapshot] :as batch-db} list-id list-hash tid start-id]
   (coll/eduction
    (comp (u/reference-resource-handle-mapper batch-db)
          (map ih/from-resource-handle))
    (r-sp-v/prefix-keys snapshot list-tid list-id list-hash item-c-hash
                        codec/tid-size (codec/tid-id tid start-id)))))

(defrecord SearchParamList [name type code]
  p/WithOrderedIndexHandles
  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values]
    (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
      (u/union-index-handles (map index-handles compiled-values))))

  (-ordered-index-handles
    [search-param batch-db tid modifier compiled-values start-id]
    (let [index-handles #(p/-index-handles search-param batch-db tid modifier % start-id)]
      (u/union-index-handles (map index-handles compiled-values))))

  p/SearchParam
  (-compile-value [_ _ value]
    (codec/id-byte-string value))

  (-estimated-scan-size [_ _ _ _ _]
    (ba/unsupported))

  (-index-handles [_ batch-db tid _ list-id]
    (if-let [hash (list-hash batch-db list-id)]
      (referenced-index-handles batch-db list-id hash tid)
      []))

  (-index-handles [_ batch-db tid _ list-id start-id]
    (if-let [hash (list-hash batch-db list-id)]
      (referenced-index-handles batch-db list-id hash tid start-id)
      []))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-second-pass-filter [_ _ _])

  (-index-values [_ _ _]
    []))

(defmethod special/special-search-param "_list"
  [_ _]
  (->SearchParamList "_list" "special" "_list"))
