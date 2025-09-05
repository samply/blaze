(ns blaze.db.impl.search-param
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param.composite]
   [blaze.db.impl.search-param.date]
   [blaze.db.impl.search-param.has]
   [blaze.db.impl.search-param.list]
   [blaze.db.impl.search-param.number]
   [blaze.db.impl.search-param.quantity]
   [blaze.db.impl.search-param.string]
   [blaze.db.impl.search-param.token]
   [blaze.db.impl.search-param.util :as u]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.references :as fsr]
   [blaze.util :refer [str]]))

(defn compile-values
  "Compiles `values` according to `search-param`.

  Returns an anomaly on errors."
  [search-param modifier values]
  (reduce
   (fn [ret value]
     (if-ok [compiled-value (p/-compile-value search-param modifier value)]
       (conj ret compiled-value)
       reduced))
   []
   values))

(defn estimated-scan-size
  "Returns a relative estimation of the amount of work to do while scanning the
  index of `search-param` with `compiled-values` under `tid`.

  The metric is relative and unitless. It can be only used to compare the amount
  of scan work between different search params.

  Returns an anomaly on errors."
  [search-param batch-db tid modifier compiled-values]
  (transduce
   (comp (map #(p/-estimated-scan-size search-param batch-db tid modifier %))
         (halt-when ba/anomaly?))
   + compiled-values))

(defn index-handles
  "Returns a reducible collection of index handles from `batch-db` of type
  with `tid` that satisfy at least one of the `compiled-values` at
  `search-param` with `modifier`, starting with `start-id` (optional).

  The `start-id` can be only used if there is only one single compiled value.

  The index handles are not distinct and not ordered by id."
  ([search-param batch-db tid modifier compiled-values]
   (if (= 1 (count compiled-values))
     (p/-index-handles search-param batch-db tid modifier (first compiled-values))
     (coll/eduction
      (mapcat #(p/-index-handles search-param batch-db tid modifier %))
      compiled-values)))
  ([search-param batch-db tid modifier compiled-values start-id]
   (assert (= 1 (count compiled-values)))
   (p/-index-handles search-param batch-db tid modifier (first compiled-values) start-id)))

(defn sorted-index-handles
  "Returns a reducible collection of index handles sorted by `search-param` in
  `direction`, starting with `start-id` (optional).

  The index handles are not distinct and not ordered by id."
  ([search-param batch-db tid direction]
   (p/-sorted-index-handles search-param batch-db tid direction))
  ([search-param batch-db tid direction start-id]
   (p/-sorted-index-handles search-param batch-db tid direction start-id)))

(defn ordered-index-handles
  "Returns an iterable of index handles from `batch-db` of type with `tid` that
  satisfy at least one of the `compiled-values` at `search-param` with
  `modifier`, starting with `start-id` (optional).

  The index handles are distinct and ordered by id."
  ([search-param batch-db tid modifier compiled-values]
   (if (= 1 (count compiled-values))
     (p/-index-handles search-param batch-db tid modifier (first compiled-values))
     (let [index-handles #(p/-index-handles search-param batch-db tid modifier %)]
       (u/union-index-handles (map index-handles compiled-values)))))
  ([search-param batch-db tid modifier compiled-values start-id]
   (if (= 1 (count compiled-values))
     (p/-index-handles search-param batch-db tid modifier (first compiled-values) start-id)
     (p/-ordered-index-handles search-param batch-db tid modifier compiled-values start-id))))

(defn ordered-compartment-index-handles
  "Returns an iterable of index handles from `batch-db` in `compartment` of type
  with `tid` that satisfy at least one of the `compiled-values` at
  `search-param`.

  The index handles are distinct and ordered by id."
  ([search-param batch-db compartment tid compiled-values]
   (if (= 1 (count compiled-values))
     (p/-ordered-compartment-index-handles
      search-param batch-db compartment tid (first compiled-values))
     (->> (map #(p/-ordered-compartment-index-handles
                 search-param batch-db compartment tid %)
               compiled-values)
          (apply coll/union ih/id-comp ih/union))))
  ([search-param batch-db compartment tid compiled-values start-id]
   (if (= 1 (count compiled-values))
     (p/-ordered-compartment-index-handles
      search-param batch-db compartment tid (first compiled-values) start-id)
     (->> (map #(p/-ordered-compartment-index-handles
                 search-param batch-db compartment tid % start-id)
               compiled-values)
          (apply coll/union ih/id-comp ih/union)))))

(defn matcher
  "Returns a stateful transducer that filters resource handles depending on
  having one of `compiled-values` for `search-param` with `modifier`."
  [search-param batch-db modifier compiled-values]
  (p/-matcher search-param batch-db modifier compiled-values))

(defn single-version-id-matcher
  "Returns a stateful transducer that filters single-version-ids depending on
  having one of `compiled-values` for `search-param` with `modifier`."
  [search-param batch-db tid modifier compiled-values]
  (p/-single-version-id-matcher search-param batch-db tid modifier compiled-values))

(def ^:private stub-resolver
  "A resolver which only returns a resource stub with type and id from the local
  reference itself."
  (reify
    fhir-path/Resolver
    (-resolve [_ uri]
      (when-let [[type id] (some-> uri fsr/split-literal-ref)]
        {:fhir/type (keyword "fhir" type)
         :id id}))))

(defn compartment-ids
  "Returns reducible collection of all ids of compartments `resource` is part-of
  according to `search-param`."
  [search-param resource]
  (p/-compartment-ids search-param stub-resolver resource))

(defn c-hash-w-modifier [c-hash code modifier]
  (if modifier
    (codec/c-hash (str code ":" modifier))
    c-hash))

(defn index-entries
  "Returns reducible collection of index entries of `resource` with `hash` for
  `search-param` or an anomaly in case of errors."
  {:arglists '([search-param linked-compartments hash resource])}
  [{:keys [code c-hash] :as search-param} linked-compartments hash resource]
  (when-ok [triples (p/-index-values search-param stub-resolver resource)]
    (let [{:keys [id]} resource
          type (name (fhir-spec/fhir-type resource))
          tid (codec/tid type)
          id (codec/id-byte-string id)
          linked-compartments
          (mapv
           (fn [[code comp-id]]
             [(codec/c-hash code)
              (codec/id-byte-string comp-id)])
           linked-compartments)]
      (coll/eduction
       (mapcat
        (fn index-entry [[modifier value include-in-compartments?]]
          (let [c-hash (c-hash-w-modifier c-hash code modifier)]
            (transduce
             (keep
              (fn index-compartment-entry [compartment]
                (when include-in-compartments?
                  (c-sp-vr/index-entry
                   compartment
                   c-hash
                   tid
                   value
                   id
                   hash))))
             conj
             [(sp-vr/index-entry c-hash tid value id hash)
              (r-sp-v/index-entry tid id hash c-hash value)]
             linked-compartments))))
       triples))))
