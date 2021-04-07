(ns blaze.db.impl.search-param
  (:require
    [blaze.anomaly :refer [conj-anom when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.composite]
    [blaze.db.impl.search-param.date]
    [blaze.db.impl.search-param.has]
    [blaze.db.impl.search-param.list]
    [blaze.db.impl.search-param.quantity]
    [blaze.db.impl.search-param.string]
    [blaze.db.impl.search-param.token]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.spec.alpha :as s]))


(set! *warn-on-reflection* true)


(defn compile-values
  "Compiles `values` according to `search-param`.

  Returns an anomaly on errors."
  [search-param modifier values]
  (transduce
    (map #(p/-compile-value search-param modifier %))
    conj-anom
    []
    values))


(defn- drop-done-values
  "Drops all values of a conjunction that are already done in respect to
  `string-start-id`.

  This is done by looking at the first resource handle and ensure that it is
  actually the one meant to find."
  [string-start-id resource-handles values]
  (drop-while
    #(let [resource-handle (coll/first (resource-handles %))]
       (not= string-start-id (:id resource-handle)))
    values))


(defn- map-2
  "Like `map` but applies `f-first` to the first item of coll and `f-rest` to
  all other items in coll."
  [f-first f-rest]
  (map-indexed
    (fn [idx x]
      (if (zero? idx)
        (f-first x)
        (f-rest x)))))


(defn- mapcat-2 [f-first f-rest]
  (comp (map-2 f-first f-rest) cat))


(defn resource-handles
  "Returns a reducible collection of resource handles.

  Concatenates resource handles of each value in compiled `values`."
  ([search-param context tid modifier values]
   (coll/eduction
     (mapcat #(p/-resource-handles search-param context tid modifier %))
     values))
  ([search-param context tid modifier values start-id]
   (let [resource-handles-start
         #(p/-resource-handles search-param context tid modifier % start-id)]
     (->> (drop-done-values
            (codec/id-string start-id)
            resource-handles-start
            values)
          (coll/eduction
            (mapcat-2
              resource-handles-start
              #(p/-resource-handles search-param context tid modifier %)))))))


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


(def stub-resolver
  "A resolver which only returns a resource stub with type and id from the local
  reference itself."
  (reify
    fhir-path/Resolver
    (-resolve [_ uri]
      (let [res (s/conform :blaze.fhir/local-ref uri)]
        (when-not (s/invalid? res)
          (let [[type id] res]
            {:fhir/type (keyword "fhir" type)
             :id id}))))))


(defn compartment-ids
  "Returns all compartments `resource` is part-of according to `search-param`."
  [search-param resource]
  (p/-compartment-ids search-param stub-resolver resource))


(defn c-hash-w-modifier [c-hash code modifier]
  (if modifier
    (codec/c-hash (str code ":" modifier))
    c-hash))


(defn index-entries
  "Returns search index entries of `resource` with `hash` for `search-param` or
  an anomaly in case of errors."
  {:arglists '([search-param linked-compartments hash resource])}
  [{:keys [code c-hash] :as search-param} linked-compartments hash resource]
  (when-ok [values (p/-index-values search-param stub-resolver resource)]
    (let [{:keys [id]} resource
          type (name (fhir-spec/fhir-type resource))
          tid (codec/tid type)
          id (codec/id-byte-string id)]
      (coll/eduction
        (mapcat
          (fn index-entry [[modifier value]]
            (let [c-hash (c-hash-w-modifier c-hash code modifier)]
              (transduce
                (map
                  (fn index-compartment-entry [[code comp-id]]
                    (c-sp-vr/index-entry
                      [(codec/c-hash code)
                       (codec/id-byte-string comp-id)]
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
