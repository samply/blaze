(ns blaze.db.impl.search-param.list
  "https://www.hl7.org/fhir/search.html#list"
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.special :as special]
    [blaze.fhir.spec])
  (:import
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)


(def ^:private list-tid (codec/tid "List"))
(def ^:private item-c-hash (codec/c-hash "item"))


(defn- list-hash-state-t [raoi list-id t]
  (resource-as-of/hash-state-t raoi list-tid list-id t))


(defn- id [^ByteBuffer bb]
  (let [id (byte-array (.remaining bb))]
    (.get bb id)
    id))


(defn- ids [iter start-key tid]
  (coll/eduction
    (comp
      (take-while (fn [[prefix]] (bytes/starts-with? prefix start-key)))
      (map (fn [[_ value]] value))
      ;; other index entries are all v-hashes
      (filter (fn [^bytes value] (< codec/v-hash-size (alength value))))
      (map #(ByteBuffer/wrap %))
      ;; the type has to match
      (filter #(= tid (.getInt ^ByteBuffer %)))
      (map id))
    (i/keys iter codec/decode-resource-value-key start-key)))


(defn- start-key [list-id list-hash]
  (codec/resource-value-key list-tid list-id list-hash item-c-hash))


(defrecord SearchParamList [type name]
  p/SearchParam
  (-compile-values [_ values]
    (map codec/id-bytes values))

  (-resource-handles [_ _ _ rsvi raoi tid _ list-id t]
    (when-let [[list-hash state] (list-hash-state-t raoi list-id t)]
      (when-not (codec/deleted? state)
        (coll/eduction
          (mapcat
            (fn [id]
              (when-let [handle (resource-as-of/resource-handle raoi tid id t)]
                (when-not (rh/deleted? handle)
                  [handle]))))
          (ids rsvi (start-key list-id list-hash) tid)))))

  (-index-entries [_ _ _ _ _]
    []))


(defmethod special/special-search-param "_list" [_]
  (->SearchParamList "special" "_list"))
