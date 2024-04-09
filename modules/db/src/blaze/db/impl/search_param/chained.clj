(ns blaze.db.impl.search-param.chained
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.node.resource-indexer.spec]
   [blaze.db.node.spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry.spec]
   [clojure.string :as str]))

(defn targets
  "Returns a reducible collection of non-deleted resource handles that are
  referenced by `resource-handle` via a search-param with `code` having a type
  with `target-tid` (optional)."
  {:arglists
   '([batch-db resource-handle code]
     [batch-db resource-handle code target-tid])}
  ([{:keys [snapshot] :as batch-db} {:keys [tid id hash]} code]
   (coll/eduction
    (u/reference-resource-handle-mapper batch-db)
    (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash code)))
  ([{:keys [snapshot] :as batch-db} {:keys [tid id hash]} code target-tid]
   (coll/eduction
    (u/reference-resource-handle-mapper batch-db)
    (let [start-value (codec/tid-byte-string target-tid)]
      (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash code
                          (bs/size start-value) start-value)))))

(defn- search-param-not-found-msg [code type]
  (format "The search-param with code `%s` and type `%s` was not found."
          code type))

(defn- resolve-search-param [registry type code]
  (if-let [search-param (sr/get registry code type)]
    search-param
    (ba/not-found (search-param-not-found-msg code type) :http/status 400)))

(defrecord ChainedSearchParam [search-param ref-search-param ref-c-hash ref-tid ref-modifier code]
  p/SearchParam
  (-compile-value [_ modifier value]
    (p/-compile-value search-param modifier value))

  (-chunked-resource-handles [this batch-db tid modifier value]
    [(p/-resource-handles this batch-db tid modifier value)])

  (-resource-handles [_ batch-db tid modifier compiled-value]
    (coll/eduction
     (comp (map #(p/-compile-value ref-search-param ref-modifier (rh/reference %)))
           (mapcat #(p/-resource-handles ref-search-param batch-db tid modifier %))
           (distinct))
     (p/-resource-handles search-param batch-db ref-tid modifier compiled-value)))

  (-resource-handles [this batch-db tid modifier compiled-value start-id]
    (let [start-id (codec/id-string start-id)]
      (coll/eduction
       (drop-while #(not= start-id (rh/id %)))
       (p/-resource-handles this batch-db tid modifier compiled-value))))

  (-matcher [_ batch-db modifier values]
    (filter
     (fn [resource-handle]
       (transduce
        (p/-matcher search-param batch-db modifier values)
        (fn ([r] r) ([_ _] (reduced true)))
        nil
        (targets batch-db resource-handle ref-c-hash ref-tid))))))

(defn- chained-search-param
  [registry ref-search-param ref-type ref-modifier original-code [code modifier]]
  (when-ok [search-param (resolve-search-param registry ref-type code)]
    [(->ChainedSearchParam search-param ref-search-param
                           (codec/c-hash (:code ref-search-param))
                           (codec/tid ref-type) ref-modifier original-code)
     modifier]))

(defn- reference-type-msg [ref-code s type]
  (format "The search parameter with code `%s` in the chain `%s` must be of type reference but has type `%s`."
          ref-code s type))

(defn- ambiguous-target-type-msg [types s]
  (format "Ambiguous target types `%s` in the chain `%s`. Please use a modifier to constrain the type."
          types s))

(defn parse-search-param [registry type s]
  (let [chain (str/split s #"\.")]
    (case (count chain)
      1
      (let [[code :as ret] (str/split (first chain) #":" 2)]
        (when-ok [search-param (resolve-search-param registry type code)]
          (assoc ret 0 search-param)))

      2
      (let [[[ref-code ref-modifier] code-modifier] (mapv #(str/split % #":" 2) chain)]
        (when-ok [{:keys [type target] :as ref-search-param} (resolve-search-param registry type ref-code)]
          (cond
            (not= "reference" type)
            (ba/incorrect (reference-type-msg ref-code s type))

            (= 1 (count target))
            (chained-search-param registry ref-search-param (first target)
                                  ref-modifier s code-modifier)

            ref-modifier
            (chained-search-param registry ref-search-param ref-modifier
                                  ref-modifier s code-modifier)

            :else
            (ba/incorrect (ambiguous-target-type-msg (str/join ", " target) s)))))

      (ba/unsupported "Search parameter chains longer than 2 are currently not supported. Please file an issue."))))
