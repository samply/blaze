(ns blaze.db.impl.search-param
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.composite]
    [blaze.db.impl.search-param.date]
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
  ""
  [search-param values]
  (mapv #(p/-compile-value search-param %) values))


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
    (let [{:keys [id]} resource
          type (name (fhir-spec/fhir-type resource))
          tid (codec/tid type)
          id-bytes (codec/id-bytes id)]
      (into
        []
        (mapcat
          (fn search-param-entry [[modifier value]]
            (log/trace "search-param-entry" code type id hash (codec/hex value))
            (let [c-hash (c-hash-w-modifier c-hash code modifier)]
              (into
                [[:search-param-value-index
                  (codec/sp-value-resource-key
                    c-hash
                    tid
                    value
                    id-bytes
                    hash)
                  bytes/empty]
                 [:resource-value-index
                  (codec/resource-sp-value-key
                    tid
                    id-bytes
                    hash
                    c-hash
                    value)
                  bytes/empty]]
                (map
                  (fn [[code id]]
                    [:compartment-search-param-value-index
                     (codec/compartment-search-param-value-key
                       (codec/c-hash code)
                       (codec/id-bytes id)
                       c-hash
                       tid
                       value
                       id-bytes
                       hash)
                     bytes/empty]))
                linked-compartments))))
        (p/-index-values search-param stub-resolver resource)))))
