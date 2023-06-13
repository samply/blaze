(ns blaze.db.impl.search-param.has
  "https://www.hl7.org/fhir/search.html#has"
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.async.comp :as ac]
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


(defn- split-modifier [modifier]
  (if modifier
    (let [[type chain-code code] (str/split modifier #":")]
      (cond
        (str/blank? type)
        (ba/incorrect (format "Missing type in _has search param `_has:%s`." modifier))

        (str/blank? chain-code)
        (ba/incorrect (format "Missing chaining search param in _has search param `_has:%s`." modifier))

        (str/blank? code)
        (ba/incorrect (format "Missing search param in _has search param `_has:%s`." modifier))

        :else
        [type chain-code code]))
    (ba/incorrect "Missing modifier of _has search param.")))


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
          {:keys [tid id hash]} resource-handle]
      (r-sp-v/prefix-keys! rsvi tid (codec/id-byte-string id) hash c-hash
                           tid-byte-string))))


(def ^:private id-cmp
  (reify Comparator
    (compare [_ a b]
      (let [^String id-a (rh/id a)]
        (.compareTo id-a (rh/id b))))))


(defn- drop-lesser-ids [start-id]
  (drop-while #(neg? (let [^String id (rh/id %)] (.compareTo id start-id)))))


(defn- resource-handles*
  [context tid [search-param chain-search-param join-tid value]]
  (into
    (sorted-set-by id-cmp)
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
    (when-ok [[type chain-code code] (split-modifier modifier)
              search-param (resolve-search-param index type code)
              chain-search-param (resolve-search-param index type chain-code)]
      [search-param
       chain-search-param
       (codec/tid type)
       (p/-compile-value search-param nil value)]))

  (-resource-handles [_ context tid _ value]
    (resource-handles context tid value))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (drop-lesser-ids (codec/id-string start-id))
      (resource-handles context tid value)))

  (-count-resource-handles [search-param context tid modifier value]
    (ac/completed-future
      (count (p/-resource-handles search-param context tid modifier value))))

  (-matches? [_ context resource-handle _ values]
    (some? (some #(matches? context resource-handle %) values)))

  (-index-values [_ _ _]
    []))


(defmethod special/special-search-param "_has"
  [index _]
  (->SearchParamHas index "_has" "special" "_has"))
