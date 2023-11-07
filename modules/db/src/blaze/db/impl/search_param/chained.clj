(ns blaze.db.impl.search-param.chained
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.async.comp :as ac]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.macros :refer [with-open-coll]]
    [blaze.db.impl.protocols :as p]
    [blaze.db.kv :as kv]
    [blaze.db.node.resource-indexer.spec]
    [blaze.db.node.spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.search-param-registry.spec]
    [clojure.string :as str]))


(defn- search-param-not-found-msg [code type]
  (format "The search-param with code `%s` and type `%s` was not found."
          code type))


(defn- resolve-search-param [registry type code]
  (if-let [search-param (sr/get registry code type)]
    search-param
    (ba/not-found (search-param-not-found-msg code type) :http/status 400)))


(defrecord ChainedSearchParam [search-param ref-search-param ref-tid ref-modifier code]
  p/SearchParam
  (-compile-value [_ modifier value]
    (p/-compile-value search-param modifier value))

  (-resource-handles [_ context tid modifier compiled-value]
    (coll/eduction
      (comp (map #(p/-compile-value ref-search-param ref-modifier (rh/reference %)))
            (mapcat #(p/-resource-handles ref-search-param context tid modifier %))
            (distinct))
      ;; TODO: improve
      (with-open-coll [svri (kv/new-iterator (:snapshot context) :search-param-value-index)]
        (p/-resource-handles search-param (assoc context :svri svri) ref-tid
                             modifier compiled-value))))

  (-resource-handles [this context tid modifier compiled-value start-id]
    (let [start-id (codec/id-string start-id)]
      (coll/eduction
        (drop-while #(not= start-id (rh/id %)))
        (p/-resource-handles this context tid modifier compiled-value))))

  (-count-resource-handles [this context tid modifier compiled-value]
    (ac/completed-future
      (count (p/-resource-handles this context tid modifier compiled-value))))

  (-matches? [_ context resource-handle modifier compiled-values]
    (coll/some
      #(p/-matches? search-param context % modifier compiled-values)
      (index/targets! context resource-handle
                      (codec/c-hash (:code ref-search-param)) ref-tid))))


(defn- chained-search-param
  [registry ref-search-param ref-type ref-modifier original-code [code modifier]]
  (when-ok [search-param (resolve-search-param registry ref-type code)]
    [(->ChainedSearchParam search-param ref-search-param (codec/tid ref-type)
                           ref-modifier original-code)
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
