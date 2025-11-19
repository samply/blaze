(ns blaze.db.impl.search-param.has
  "https://www.hl7.org/fhir/search.html#has"
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.special :as special]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir.spec]
   [clojure.string :as str])
  (:import
   [blaze.db.impl.index ResourceHandle]
   [com.github.benmanes.caffeine.cache Cache Caffeine]
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
  {:arglists '([batch-db search-param tid resource-handle])}
  [{:keys [snapshot] :as batch-db} {:keys [c-hash]} tid resource-handle]
  (coll/eduction
   (u/reference-resource-handle-mapper batch-db)
   (let [tid-byte-string (codec/tid-byte-string tid)
         {:keys [tid id hash]} resource-handle]
     (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash c-hash
                         (bs/size tid-byte-string) tid-byte-string))))

(defn- drop-lesser-ids [start-id]
  (drop-while #(neg? (let [^String id (:id %)] (.compareTo id start-id)))))

(defn- resource-handles*
  [batch-db tid [search-param chain-search-param join-tid value]]
  (into
   (sorted-set-by ResourceHandle/ID_CMP)
   (comp (u/resource-handle-xf batch-db join-tid)
         (mapcat (partial resolve-resource-handles batch-db chain-search-param tid)))
   (p/-index-handles search-param batch-db join-tid nil value)))

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
  {:arglists '([batch-db tid [search-param chain-search-param join-tid value]])}
  [{:keys [t] :as batch-db} tid
   [{:keys [c-hash]} {chain-c-hash :c-hash} join-tid value :as data]]
  (let [key [t tid join-tid chain-c-hash c-hash value]]
    (.get resource-handles-cache key (fn [_] (resource-handles* batch-db tid data)))))

(defn- matches?
  [batch-db {:keys [tid] :as resource-handle} values]
  (some #(contains? (resource-handles batch-db tid %) resource-handle) values))

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

  (-estimated-scan-size [_ _ _ _ _]
    (ba/unsupported))

  (-supports-ordered-index-handles [_ _ _ _ _]
    false)

  (-ordered-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-index-handles [_ batch-db tid _ compiled-value]
    (coll/eduction
     (map ih/from-resource-handle)
     (resource-handles batch-db tid compiled-value)))

  (-index-handles [_ batch-db tid _ compiled-value start-id]
    (coll/eduction
     (comp (drop-lesser-ids (codec/id-string start-id))
           (map ih/from-resource-handle))
     (resource-handles batch-db tid compiled-value)))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db _ compiled-values]
    (filter #(matches? batch-db % compiled-values)))

  (-single-version-id-matcher [search-param batch-db tid modifier compiled-values]
    (comp (map ih/from-single-version-id)
          (u/resource-handle-xf batch-db tid)
          (p/-matcher search-param batch-db modifier compiled-values)
          (map svi/from-resource-handle)))

  (-second-pass-filter [_ _ _])

  (-index-values [_ _ _]
    []))

(defmethod special/special-search-param "_has"
  [index _]
  (->SearchParamHas index "_has" "special" "_has"))
