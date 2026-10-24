(ns blaze.db.impl.search-param.chained
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
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

(set! *warn-on-reflection* true)

(defn targets
  "Returns a reducible collection of non-deleted resource handles that are
  referenced by `resource-handle` via a search-param with `c-hash` having a type
  with `target-tid` (optional).

  Example: a patient that is referenced by an observation via the subject search
  param."
  {:arglists
   '([db resource-handle c-hash]
     [db resource-handle c-hash target-tid])}
  ([{:keys [snapshot] :as db} {:keys [tid id hash-prefix]} c-hash]
   (coll/eduction
    (u/reference-resource-handle-mapper db)
    (r-sp-v/prefix-keys snapshot tid id hash-prefix c-hash)))
  ([{:keys [snapshot] :as db} {:keys [tid id hash-prefix]} c-hash
    target-tid]
   (coll/eduction
    (u/reference-resource-handle-mapper db)
    (let [start-value (codec/tid-byte-string target-tid)]
      (r-sp-v/prefix-keys snapshot tid id hash-prefix c-hash
                          (bs/size start-value) start-value)))))

(defn target-ids
  "Returns a reducible collection of resource ids that are referenced by
  `resource-handle` via a search-param with `c-hash` having a type with
  `target-tid`.

  Example: a patient that is referenced by an observation via the subject search
  param."
  {:arglists
   '([db resource-handle c-hash target-tid])}
  [{:keys [snapshot]} {:keys [tid id hash-prefix]} c-hash target-tid]
  (coll/eduction
   u/reference-id-mapper
   (let [start-value (codec/tid-byte-string target-tid)]
     (r-sp-v/prefix-keys snapshot tid id hash-prefix
                         c-hash (bs/size start-value) start-value))))

(defn single-version-id-targets
  "Returns a reducible collection of single-version-ids that are referenced by
  `single-version-id` via a search-param with `c-hash` having a type with
  `target-tid`."
  {:arglists '([db tid single-version-id c-hash target-tid])}
  [{:keys [snapshot] :as db} tid single-version-id c-hash target-tid]
  (coll/eduction
   (comp (u/reference-resource-handle-mapper db)
         (map svi/from-resource-handle))
   (let [start-value (codec/tid-byte-string target-tid)]
     (r-sp-v/prefix-keys snapshot tid (svi/id single-version-id)
                         (svi/hash-prefix single-version-id) c-hash
                         (bs/size start-value) start-value))))

(defn- reference [rh]
  (str (name (:fhir/type rh)) "/" (:id rh)))

(def ^:private max-number-of-refs
  "Maximum number of References that can be handled in ordered index handle mode."
  10000)

(defn- ref-resource-handles
  "Returns an unordered reducible collection of reference resource handles of
  all `compiled-values`."
  [search-param db ref-tid modifier compiled-values]
  (coll/eduction
   (comp (mapcat #(p/-index-handles search-param db ref-tid modifier %))
         (u/resource-handle-xf db ref-tid)
         u/distinct-by-id)
   compiled-values))

(defn- compiled-ref-values
  "Returns a non-empty vector of compiled ref values or nil.

  Only used in ordered index handle mode, so the number of ref values is
  limited to `max-number-of-refs`."
  [search-param db ref-tid compile-ref-value modifier compiled-values]
  (not-empty
   (into
    []
    (map compile-ref-value)
    (ref-resource-handles search-param db ref-tid modifier
                          compiled-values))))

(defn- num-ref-resource-handles-small? [ref-resource-handles]
  (<= (count (coll/eduction (take (inc max-number-of-refs)) ref-resource-handles))
      max-number-of-refs))

(defrecord ChainedSearchParam [search-param ref-search-param ref-c-hash ref-tid
                               code compile-ref-value]
  p/SearchParam
  (-validate-modifier [_ modifier]
    (p/-validate-modifier search-param modifier))

  (-compile-value [_ modifier value]
    (p/-compile-value search-param modifier value))

  (-estimated-scan-size [_ db tid modifier compiled-value]
    (transduce
     (comp (map compile-ref-value)
           (map #(p/-estimated-scan-size ref-search-param db tid nil %))
           (halt-when ba/anomaly?))
     +
     (ref-resource-handles search-param db ref-tid modifier
                           [compiled-value])))

  (-supports-ordered-index-handles
    [_ db _ modifier compiled-values]
    (-> (ref-resource-handles search-param db ref-tid modifier
                              compiled-values)
        (num-ref-resource-handles-small?)))

  (-ordered-index-handles
    [_ db tid modifier compiled-values]
    (if-some [values (compiled-ref-values search-param db ref-tid
                                          compile-ref-value modifier
                                          compiled-values)]
      (p/-ordered-index-handles ref-search-param db tid nil values)
      []))

  (-ordered-index-handles
    [_ db tid modifier compiled-values start-id]
    (if-some [values (compiled-ref-values search-param db ref-tid
                                          compile-ref-value modifier
                                          compiled-values)]
      (p/-ordered-index-handles ref-search-param db tid nil values start-id)
      []))

  (-index-handles [_ db tid modifier compiled-value]
    (coll/eduction
     (comp (u/resource-handle-xf db ref-tid)
           (map compile-ref-value)
           (mapcat #(p/-index-handles ref-search-param db tid modifier %)))
     (p/-index-handles search-param db ref-tid modifier compiled-value)))

  (-index-handles [this db tid modifier compiled-value start-id]
    (coll/eduction
     (comp (u/resource-handle-xf db tid)
           u/distinct-by-id
           (u/drop-while-not-start-id start-id)
           (map ih/from-resource-handle))
     (p/-index-handles this db tid modifier compiled-value)))

  (-supports-ordered-compartment-index-handles [_ _ _]
    false)

  (-ordered-compartment-index-handles [_ _ _ _ _ _]
    (ba/unsupported))

  (-ordered-compartment-index-handles [_ _ _ _ _ _ _]
    (ba/unsupported))

  (-matcher [_ db modifier values]
    (filter
     (fn [resource-handle]
       (transduce
        (p/-matcher search-param db modifier values)
        (fn ([r] r) ([_ _] (reduced true)))
        nil
        (targets db resource-handle ref-c-hash ref-tid)))))

  (-single-version-id-matcher [_ db tid modifier values]
    (filter
     (fn [single-version-id]
       (transduce
        (p/-single-version-id-matcher search-param db ref-tid modifier values)
        (fn ([r] r) ([_ _] (reduced true)))
        nil
        (single-version-id-targets db tid single-version-id ref-c-hash ref-tid)))))

  (-postprocess-matches [_ _ _ _ _]))

(defn- compile-ref-search-param-value
  "Compiles the value of the reference of `resource-handle` via
  `ref-search-param`.

  Because compiling references isn't really async, we can join here."
  [ref-search-param resource-handle]
  (ac/join (p/-compile-value ref-search-param nil (reference resource-handle))))

(defn chained-search-param
  "Creates a new chaining search param from the following arguments:

  * search-param     - the second search param in the chain which can be any
                       search param like Condition.code or Patient.gender
  * ref-search-param - the first search param in the chain which has to be a
                       reference search param like Encounter.diagnosis or
                       Observation.subject
  * ref-type         - the type of the resources referenced by the first search
                       param
  * original-code    - the original code like diagnosis:Condition.code or
                       subject:Patient.gender
  * modifier         - modifier of `search-param`"
  [search-param ref-search-param ref-type original-code modifier]
  [(->ChainedSearchParam search-param ref-search-param
                         (codec/c-hash (:code ref-search-param))
                         (codec/tid ref-type) original-code
                         #(compile-ref-search-param-value ref-search-param %))
   modifier])
