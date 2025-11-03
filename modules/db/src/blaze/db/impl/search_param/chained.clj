(ns blaze.db.impl.search-param.chained
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.single-version-id :as svi]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.node.resource-indexer.spec]
   [blaze.db.node.spec]
   [blaze.db.search-param-registry.spec]))

(defn targets
  "Returns a reducible collection of non-deleted resource handles that are
  referenced by `resource-handle` via a search-param with `c-hash` having a type
  with `target-tid` (optional).

  Example: a patient that is referenced by an observation via the subject search
  param."
  {:arglists
   '([batch-db resource-handle c-hash]
     [batch-db resource-handle c-hash target-tid])}
  ([{:keys [snapshot] :as batch-db} {:keys [tid id hash]} c-hash]
   (coll/eduction
    (u/reference-resource-handle-mapper batch-db)
    (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash c-hash)))
  ([{:keys [snapshot] :as batch-db} {:keys [tid id hash]} c-hash target-tid]
   (coll/eduction
    (u/reference-resource-handle-mapper batch-db)
    (let [start-value (codec/tid-byte-string target-tid)]
      (r-sp-v/prefix-keys snapshot tid (codec/id-byte-string id) hash c-hash
                          (bs/size start-value) start-value)))))

(defn single-version-id-targets
  "Returns a reducible collection of single-version-ids that are referenced by
  `single-version-id` via a search-param with `c-hash` having a type with
  `target-tid`."
  {:arglists '([batch-db tid single-version-id c-hash target-tid])}
  [{:keys [snapshot] :as batch-db} tid single-version-id c-hash target-tid]
  (coll/eduction
   (comp (u/reference-resource-handle-mapper batch-db)
         (map svi/from-resource-handle))
   (let [start-value (codec/tid-byte-string target-tid)]
     (r-sp-v/hash-prefix-prefix-keys snapshot tid (svi/id single-version-id)
                                     (svi/hash-prefix single-version-id) c-hash
                                     (bs/size start-value) start-value))))

(defn- reference [rh]
  (str (name (:fhir/type rh)) "/" (:id rh)))

(defrecord ChainedSearchParam [search-param ref-search-param ref-c-hash ref-tid
                               ref-modifier code]
  p/SearchParam
  (-compile-value [_ modifier value]
    (p/-compile-value search-param modifier value))

  (-estimated-scan-size [_ _ _ _ _]
    (ba/unsupported))

  (-index-handles [_ batch-db tid modifier compiled-value]
    (coll/eduction
     (comp (u/resource-handle-xf batch-db ref-tid)
           (map #(p/-compile-value ref-search-param ref-modifier (reference %)))
           (mapcat #(p/-index-handles ref-search-param batch-db tid modifier %)))
     (p/-index-handles search-param batch-db ref-tid modifier compiled-value)))

  (-index-handles [this batch-db tid modifier compiled-value start-id]
    (let [start-id (codec/id-string start-id)]
      (coll/eduction
       (comp (u/resource-handle-xf batch-db tid)
             (distinct)
             (drop-while #(not= start-id (:id %)))
             (map ih/from-resource-handle))
       (p/-index-handles this batch-db tid modifier compiled-value))))

  (-supports-ordered-compartment-index-handles [_ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ batch-db modifier values]
    (filter
     (fn [resource-handle]
       (transduce
        (p/-matcher search-param batch-db modifier values)
        (fn ([r] r) ([_ _] (reduced true)))
        nil
        (targets batch-db resource-handle ref-c-hash ref-tid)))))

  (-single-version-id-matcher [_ batch-db tid modifier values]
    (filter
     (fn [single-version-id]
       (transduce
        (p/-single-version-id-matcher search-param batch-db ref-tid modifier values)
        (fn ([r] r) ([_ _] (reduced true)))
        nil
        (single-version-id-targets batch-db tid single-version-id ref-c-hash ref-tid)))))

  (-second-pass-filter [_ _ _]))

(defn chained-search-param
  "Creates a new chaining search param from the following arguments:

  * search-param     - the first search param in the chain which has to be a
                       reference search param like Encounter.diagnosis or
                       Observation.subject
  * ref-search-param - the second search param in the chain which can be any
                       search param like Condition.code or Patient.gender
  * ref-type         - the type of the resources referenced by the first search
                       param
  * ref-modifier     - the modifier of `ref-search-param`
  * original-code    - the original code like diagnosis:Condition.code or
                       subject:Patient.gender
  * modifier         - modifier of `search-param`"
  [search-param ref-search-param ref-type ref-modifier original-code modifier]
  [(->ChainedSearchParam search-param ref-search-param
                         (codec/c-hash (:code ref-search-param))
                         (codec/tid ref-type) ref-modifier original-code)
   modifier])
