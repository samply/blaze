(ns blaze.db.impl.search-param
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.date]
    [blaze.db.impl.search-param.list]
    [blaze.db.impl.search-param.quantity]
    [blaze.db.impl.search-param.string]
    [blaze.db.impl.search-param.token]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir-path :as fhir-path]
    [clojure.spec.alpha :as s]))


(set! *warn-on-reflection* true)


(defn compile-values
  ""
  [search-param values]
  (p/-compile-values search-param values))


(defn resource-handles
  "Returns a reducible collection of resource handles."
  [search-param context tid modifier compiled-values start-id]
  (coll/eduction
    (mapcat #(p/-resource-handles search-param context tid modifier % start-id))
    compiled-values))


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


(defn matches? [search-param context tid id hash modifier compiled-values]
  (p/-matches? search-param context tid id hash modifier compiled-values))


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


(defn index-entries
  "Returns search index entries of `resource` with `hash` or an anomaly in case
  of errors."
  [search-param hash resource linked-compartments]
  (p/-index-entries search-param stub-resolver hash resource linked-compartments))
