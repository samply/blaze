(ns blaze.db.impl.search-param.has
  "https://www.hl7.org/fhir/search.html#has"
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string :as bs]
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
   [java.util.concurrent TimeUnit]))

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
  the resource with `resource-handle` by `search-param`.

  Example:
   * `search-param`    - Encounter.subject
   * `tid`             - Patient
   * `resource-handle` - an Encounter
   * result            - a coll with one Patient"
  {:arglists '([context search-param tid resource-handle])}
  [{:keys [snapshot] :as context} {:keys [c-hash]} tid resource-handle]
  (coll/eduction
   (u/reference-resource-handle-mapper context)
   (let [tid-byte-string (codec/tid-byte-string tid)
         {:keys [tid id hash]} resource-handle]
     (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash c-hash
                         (bs/size tid-byte-string) tid-byte-string))))

(def ^:private id-cmp
  (reify Comparator
    (compare [_ a b]
      (.compareTo ^String (rh/id a) (rh/id b)))))

(defn- drop-lesser-ids [start-id]
  (drop-while #(neg? (let [^String id (rh/id %)] (.compareTo id start-id)))))

(defn- resource-handles*
  [context tid [search-param chain-search-param join-tid value]]
  (into
   (sorted-set-by id-cmp)
   (mapcat (partial resolve-resource-handles context chain-search-param tid))
   (p/-resource-handles search-param context join-tid nil value)))

;; TODO: make this cache public and configurable?
(def ^:private ^Cache resource-handles-cache
  (-> (Caffeine/newBuilder)
      (.maximumSize 100)
      (.expireAfterAccess 1 TimeUnit/MINUTES)
      (.build)))

(defn- resource-handles
  "Returns a sorted set of resource handles of resources of type `tid`,
  referenced from resources of the type `join-tid` by `chain-search-param` that
  have `value` according to `search-param`."
  {:arglists '([context tid [search-param chain-search-param join-tid value]])}
  [{:keys [t] :as context} tid
   [{:keys [c-hash]} {chain-c-hash :c-hash} join-tid value :as data]]
  (let [key [t tid join-tid chain-c-hash c-hash value]]
    (.get resource-handles-cache key (fn [_] (resource-handles* context tid data)))))

(defn- matches?
  [context {:keys [tid] :as resource-handle} values]
  (some #(contains? (resource-handles context tid %) resource-handle) values))

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

  (-resource-handles [_ batch-db tid _ value]
    (resource-handles batch-db tid value))

  (-resource-handles [_ batch-db tid _ value start-id]
    (coll/eduction
     (drop-lesser-ids (codec/id-string start-id))
     (resource-handles batch-db tid value)))

  (-chunked-resource-handles [search-param batch-db tid modifier value]
    [(p/-resource-handles search-param batch-db tid modifier value)])

  (-matcher [_ batch-db _ values]
    (filter #(matches? batch-db % values)))

  (-index-values [_ _ _]
    []))

(defmethod special/special-search-param "_has"
  [index _]
  (->SearchParamHas index "_has" "special" "_has"))
