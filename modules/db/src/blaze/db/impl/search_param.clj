(ns blaze.db.impl.search-param
  (:require
    [blaze.anomaly :refer [conj-anom when-ok]]
    [blaze.byte-string :as bs]
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
    [clojure.spec.alpha :as s]
    [taoensso.timbre :as log]))


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


(defn resource-handles
  "Returns a reducible collection of resource handles."
  ([search-param context tid modifier compiled-values]
   (coll/eduction
     (mapcat #(p/-resource-handles search-param context tid modifier %))
     compiled-values))
  ([search-param context tid modifier compiled-values start-id]
   (coll/eduction
     (mapcat #(p/-resource-handles search-param context tid modifier % start-id))
     compiled-values)))


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
  "Returns search index entries of `resource` with `hash` or an anomaly in case
  of errors."
  [{:keys [type code c-hash] :as search-param} hash resource linked-compartments]
  (case type
    "date"
    (p/-index-entries search-param stub-resolver hash resource linked-compartments)
    (when-ok [values (p/-index-values search-param stub-resolver resource)]
      (let [{:keys [id]} resource
            type (name (fhir-spec/fhir-type resource))
            tid (codec/tid type)]
        (into
          []
          (mapcat
            (fn search-param-entry [[modifier value]]
              (log/trace "search-param-entry" code type id (bs/hex hash) (bs/hex value))
              (let [c-hash (c-hash-w-modifier c-hash code modifier)
                    id (codec/id-byte-string id)]
                (into
                  [(sp-vr/index-entry c-hash tid value id hash)
                   (r-sp-v/index-entry tid id hash c-hash value)]
                  (map
                    (fn [[code comp-id]]
                      (c-sp-vr/index-entry
                        [(codec/c-hash code)
                         (codec/id-byte-string comp-id)]
                        c-hash
                        tid
                        value
                        id
                        hash)))
                  linked-compartments))))
          values)))))
