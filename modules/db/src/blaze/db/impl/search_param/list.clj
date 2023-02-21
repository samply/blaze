(ns blaze.db.impl.search-param.list
  "https://www.hl7.org/fhir/search.html#list"
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.special :as special]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir.spec]))


(set! *warn-on-reflection* true)


(def ^:private ^:const list-tid (codec/tid "List"))
(def ^:private ^:const item-c-hash (codec/c-hash "item"))


(defn- list-resource-handle [{:keys [resource-id resource-handle]} list-id]
  (when-let [list-did (resource-id list-tid list-id)]
    (u/non-deleted-resource-handle resource-handle list-tid list-did)))


(defn- referenced-resource-handles!
  "Returns a reducible collection of resource handles of type `tid` that are
  referenced by the list with `list-did` and `list-hash`, starting with
  `start-did` (optional).

  Changes the state of `rsvi`. Consuming the collection requires exclusive
  access to `rsvi`. Doesn't close `rsvi`."
  {:arglists
   '([context list-did list-hash tid]
     [context list-did list-hash tid start-did])}
  ([{:keys [rsvi] :as context} list-did list-hash tid]
   (coll/eduction
     (u/reference-resource-handle-mapper context)
     (r-sp-v/prefix-keys! rsvi list-tid list-did list-hash item-c-hash
                          (codec/tid-byte-string tid))))
  ([{:keys [rsvi] :as context} list-did list-hash tid start-did]
   (coll/eduction
     (u/reference-resource-handle-mapper context)
     (r-sp-v/prefix-keys! rsvi list-tid list-did list-hash item-c-hash
                          (codec/tid-byte-string tid)
                          (codec/tid-did tid start-did)))))


(defrecord SearchParamList [name type code]
  p/SearchParam
  (-compile-value [_ _modifier list-id]
    list-id)

  (-resource-handles [_ context tid _ list-id]
    (when-let [{:keys [did hash]} (list-resource-handle context list-id)]
      (referenced-resource-handles! context did hash tid)))

  (-resource-handles [_ context tid _ list-id start-did]
    (when-let [{:keys [did hash]} (list-resource-handle context list-id)]
      (referenced-resource-handles! context did hash tid start-did)))

  (-index-values [_ _ _ _]
    []))


(defmethod special/special-search-param "_list"
  [_ _]
  (->SearchParamList "_list" "special" "_list"))
