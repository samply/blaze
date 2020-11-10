(ns blaze.db.impl.search-param.has
  "https://www.hl7.org/fhir/search.html#has"
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.special :as special]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir.spec]
    [clojure.string :as str]
    [cognitect.anomalies :as anom])
  (:import
    [com.github.benmanes.caffeine.cache Cache Caffeine]
    [java.util Comparator]
    [java.util.concurrent TimeUnit]
    [java.util.function Function]))


(set! *warn-on-reflection* true)


(defn- resolve-search-param [index type code]
  (if-let [search-param (get-in index [type code])]
    search-param
    {::anom/category ::anom/not-found
     ::anom/message (format "The search-param with code `%s` and type `%s` was not found." code type)}))


(defn- start-key [c-hash resource-handle]
  (codec/resource-sp-value-key
    (rh/tid resource-handle)
    (codec/id-bytes (rh/id resource-handle))
    (rh/hash resource-handle)
    c-hash))


(defn- resolve-resource-handles
  "Resolves a coll of resource handles of resources referenced in the resource
  with `resource-handle` by `search-param`."
  [{:keys [rsvi] :as context} {:keys [c-hash]} tid resource-handle]
  (let [start-key (start-key c-hash resource-handle)]
    (into
      []
      (comp
        (take-while (fn [[prefix]] (bytes/starts-with? prefix start-key)))
        (u/reference-resource-handle-mapper context tid))
      (i/keys rsvi codec/decode-resource-sp-value-key start-key))))


(def ^:private id-cmp
  (reify Comparator
    (compare [_ a b]
      (.compareTo ^String (rh/id a) (rh/id b)))))


(defn- drop-lesser-ids [start-id]
  (drop-while #(neg? (.compareTo ^String (rh/id %) start-id))))


(defn- resource-handles*
  [context tid [search-param chain-search-param join-tid value]]
  (into
    (sorted-set-by id-cmp)
    (mapcat #(resolve-resource-handles context chain-search-param tid %))
    (p/-resource-handles search-param context join-tid nil value nil)))


;; TODO: make this cahce public and configurable?
(def ^:private ^Cache resource-handles-cache
  (-> (Caffeine/newBuilder)
      (.maximumSize 100)
      (.expireAfterAccess 1 TimeUnit/MINUTES)
      (.build)))


(comment
  (keys (.asMap resource-handles-cache))
  )


(defn- resource-handles
  [{:keys [t] :as context} tid
   [{:keys [c-hash]} {chain-c-hash :c-hash} join-tid value :as data]]
  (let [key [t tid join-tid chain-c-hash c-hash value]]
    (.get resource-handles-cache key
          (reify Function
            (apply [_ _]
              (resource-handles* context tid data))))))


(defn- matches?
  [context {:keys [tid] :as resource-handle} value]
  (contains? (resource-handles context tid value) resource-handle))


(defrecord SearchParamHas [index name type code]
  p/SearchParam
  (-compile-value [_ modifier value]
    (let [[type chain-code code] (str/split modifier #":")]
      (when-ok [search-param (resolve-search-param index type code)]
        (when-ok [chain-search-param (resolve-search-param index type chain-code)]
          [search-param
           chain-search-param
           (codec/tid type)
           (p/-compile-value search-param nil value)]))))

  (-resource-handles [_ context tid _ value start-id]
    (if start-id
      (coll/eduction
        (drop-lesser-ids (codec/id start-id))
        (resource-handles context tid value))
      (resource-handles context tid value)))

  (-matches? [_ context resource-handle _ values]
    (some #(matches? context resource-handle %) values))

  (-index-values [_ _ _]
    []))


(defmethod special/special-search-param "_has"
  [index _]
  (->SearchParamHas index "_has" "special" "_has"))
