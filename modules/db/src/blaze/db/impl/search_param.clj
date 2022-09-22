(ns blaze.db.impl.search-param
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
    [blaze.db.impl.index.resource-handle :as rh]
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
    [blaze.fhir.spec :as fhir-spec]))


(set! *warn-on-reflection* true)


(defn compile-values
  "Compiles `values` according to `search-param`.

  Returns an anomaly on errors."
  [search-param modifier values]
  (transduce
    (comp (map (partial p/-compile-value search-param modifier))
          (halt-when ba/anomaly?))
    conj
    values))


(defn resource-handles
  "Returns a reducible collection of distinct resource handles.

  Concatenates resource handles of each value in compiled `values`."
  ([search-param context tid modifier values]
   (if (= 1 (count values))
     (p/-resource-handles search-param context tid modifier (first values))
     (coll/eduction
       (comp
         (mapcat (partial p/-resource-handles search-param context tid modifier))
         (distinct))
       values)))
  ([search-param context tid modifier values start-id]
   (if (= 1 (count values))
     (p/-resource-handles search-param context tid modifier (first values)
                          start-id)
     (let [start-id (codec/id-string start-id)]
       (coll/eduction
         (drop-while #(not= start-id (rh/id %)))
         (resource-handles search-param context tid modifier values))))))


(defn sorted-resource-handles
  "Returns a reducible collection of distinct resource handles sorted by
  `search-param` in `direction`.

  Optionally starts at `start-id`"
  ([search-param context tid direction]
   (p/-sorted-resource-handles search-param context tid direction))
  ([search-param context tid direction start-id]
   (p/-sorted-resource-handles search-param context tid direction start-id)))


(defn- compartment-keys
  "Returns a reducible collection of `[prefix id hash-prefix]` triples."
  [search-param context compartment tid compiled-values]
  (coll/eduction
    (mapcat #(p/-compartment-keys search-param context compartment tid %))
    compiled-values))


(defn compartment-resource-handles
  [search-param context compartment tid compiled-values]
  (coll/eduction
    (u/resource-handle-mapper context tid)
    (compartment-keys search-param context compartment tid compiled-values)))


(defn matches? [search-param context resource-handle modifier compiled-values]
  (p/-matches? search-param context resource-handle modifier compiled-values))


(def ^:private stub-resolver
  "A resolver which only returns a resource stub with type and id from the local
  reference itself."
  (reify
    fhir-path/Resolver
    (-resolve [_ uri]
      (when-let [[type id] (some-> uri u/split-literal-ref)]
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
  (when-ok [values (p/-index-values search-param stub-resolver resource)]
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
          (fn index-entry [[modifier value]]
            (let [c-hash (c-hash-w-modifier c-hash code modifier)]
              (transduce
                (map
                  (fn index-compartment-entry [compartment]
                    (c-sp-vr/index-entry
                      compartment
                      c-hash
                      tid
                      value
                      id
                      hash)))
                conj
                [(sp-vr/index-entry c-hash tid value id hash)
                 (r-sp-v/index-entry tid id hash c-hash value)]
                linked-compartments))))
        values))))
