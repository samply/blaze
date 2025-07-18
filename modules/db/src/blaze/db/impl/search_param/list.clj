(ns blaze.db.impl.search-param.list
  "https://www.hl7.org/fhir/search.html#list"
  (:require
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.special :as special]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir.spec]))

(set! *warn-on-reflection* true)

(def ^:private list-tid (codec/tid "List"))
(def ^:private item-c-hash (codec/c-hash "item"))

(defn- referenced-resource-handles
  "Returns a reducible collection of resource handles of type `tid` that are
  referenced by the list with `list-id` and `list-hash`, starting with
  `start-id` (optional)."
  {:arglists
   '([context list-id list-hash tid]
     [context list-id list-hash tid start-id])}
  ([{:keys [snapshot] :as context} list-id list-hash tid]
   (coll/eduction
    (u/reference-resource-handle-mapper context)
    (let [start-value (codec/tid-byte-string tid)]
      (r-sp-v/prefix-keys snapshot list-tid list-id list-hash item-c-hash
                          (bs/size start-value) start-value))))
  ([{:keys [snapshot] :as context} list-id list-hash tid start-id]
   (coll/eduction
    (u/reference-resource-handle-mapper context)
    (r-sp-v/prefix-keys snapshot list-tid list-id list-hash item-c-hash
                        codec/tid-size (codec/tid-id tid start-id)))))

(defrecord SearchParamList [name type code]
  p/SearchParam
  (-compile-value [_ _ value]
    (codec/id-byte-string value))

  (-resource-handles [_ batch-db tid _ list-id]
    (when-let [{:keys [hash]} (u/non-deleted-resource-handle
                               batch-db list-tid list-id)]
      (referenced-resource-handles batch-db list-id hash tid)))

  (-resource-handles [_ batch-db tid _ list-id start-id]
    (when-let [{:keys [hash]} (u/non-deleted-resource-handle
                               batch-db list-tid list-id)]
      (referenced-resource-handles batch-db list-id hash tid start-id)))

  (-chunked-resource-handles [search-param batch-db tid modifier value]
    [(p/-resource-handles search-param batch-db tid modifier value)])

  (-index-values [_ _ _]
    []))

(defmethod special/special-search-param "_list"
  [_ _]
  (->SearchParamList "_list" "special" "_list"))
