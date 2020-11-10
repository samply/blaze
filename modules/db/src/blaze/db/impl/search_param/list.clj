(ns blaze.db.impl.search-param.list
  "https://www.hl7.org/fhir/search.html#list"
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.db.impl.iterators :as i]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.special :as special]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir.spec]))


(set! *warn-on-reflection* true)


(def ^:private list-tid (codec/tid "List"))
(def ^:private item-c-hash (codec/c-hash "item"))


(defn- list-hash-state-t [{:keys [raoi t]} list-id]
  (rao/hash-state-t raoi list-tid list-id t))


(defn- referenced-resource-handles*
  [{:keys [rsvi] :as context} prefix-key tid start-key]
  (coll/eduction
    (comp
      (take-while (fn [[prefix]] (bytes/starts-with? prefix prefix-key)))
      (u/reference-resource-handle-mapper context tid))
    (i/keys rsvi codec/decode-resource-sp-value-key start-key)))


(defn- start-key
  ([list-id list-hash]
   (codec/resource-sp-value-key list-tid list-id list-hash item-c-hash))
  ([list-id list-hash tid start-id]
   (codec/resource-sp-value-key list-tid list-id list-hash item-c-hash
                                (codec/tid-id tid start-id))))


(defn- referenced-resource-handles [context list-id list-hash tid start-id]
  (let [key (start-key list-id list-hash)]
    (if start-id
      (referenced-resource-handles*
        context key tid (start-key list-id list-hash tid start-id))
      (referenced-resource-handles* context key tid key))))


(defrecord SearchParamList [name type code]
  p/SearchParam
  (-compile-value [_ _ value]
    (codec/id-bytes value))

  (-resource-handles [_ context tid _ list-id start-id]
    (when-let [[list-hash state] (list-hash-state-t context list-id)]
      (when-not (codec/deleted? state)
        (referenced-resource-handles context list-id list-hash tid start-id))))

  (-index-values [_ _ _]
    []))


(defmethod special/special-search-param "_list"
  [_ _]
  (->SearchParamList "_list" "special" "_list"))
