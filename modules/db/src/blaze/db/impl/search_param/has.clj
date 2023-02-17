(ns blaze.db.impl.search-param.has
  "https://www.hl7.org/fhir/search.html#has"
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.special :as special]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir.spec]
    [clojure.string :as str])
  (:import
    [com.github.benmanes.caffeine.cache Cache Caffeine]
    [java.util Comparator]
    [java.util.concurrent TimeUnit]
    [java.util.function Function]))


(set! *warn-on-reflection* true)


(defn- search-param-not-found-msg [code type]
  (format "The search-param with code `%s` and type `%s` was not found."
          code type))


(defn- resolve-search-param [index type code]
  (if-let [search-param (get-in index [type code])]
    search-param
    (ba/not-found (search-param-not-found-msg code type))))


(defn- resolve-resource-handles
  "Resolves a coll of resource handles of resources of type `tid`, referenced in
  the resource with `resource-handle` by `search-param`."
  {:arglists '([context search-param tid resource-handle])}
  [{:keys [rsvi] :as context} {:keys [c-hash]} tid resource-handle]
  (into
    []
    (u/reference-resource-handle-mapper context)
    (let [tid-byte-string (codec/tid-byte-string tid)
          {:keys [tid did hash]} resource-handle]
      (r-sp-v/prefix-keys! rsvi tid did hash c-hash tid-byte-string))))


(def ^:private did-cmp
  (reify Comparator
    (compare [_ a b]
      (Long/compare (rh/did a) (rh/did b)))))


(defn- drop-lesser-ids [^long start-did]
  (drop-while #(< (rh/did %) start-did)))


(defn- resource-handles*
  [context tid [search-param chain-search-param join-tid value]]
  (into
    (sorted-set-by did-cmp)
    (mapcat #(resolve-resource-handles context chain-search-param tid %))
    (p/-resource-handles search-param context join-tid nil value)))


;; TODO: make this cache public and configurable?
(def ^:private ^Cache resource-handles-cache
  (-> (Caffeine/newBuilder)
      (.maximumSize 100)
      (.expireAfterAccess 1 TimeUnit/MINUTES)
      (.build)))


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

  (-resource-handles [_ context tid _ value]
    (resource-handles context tid value))

  (-resource-handles [_ context tid _ value start-did]
    (coll/eduction (drop-lesser-ids start-did) (resource-handles context tid value)))

  (-matches? [_ context resource-handle _ values]
    (some? (some #(matches? context resource-handle %) values)))

  (-index-values [_ _ _ _]
    []))


(defmethod special/special-search-param "_has"
  [index _]
  (->SearchParamHas index "_has" "special" "_has"))
