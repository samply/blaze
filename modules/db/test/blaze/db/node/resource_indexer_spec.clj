(ns blaze.db.node.resource-indexer-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.byte-string-spec]
    [blaze.db.impl.index.compartment.resource-spec]
    [blaze.db.impl.search-param-spec]
    [blaze.db.kv-spec]
    [blaze.db.node.resource-indexer :as resource-indexer]
    [blaze.db.search-param-registry.spec]
    [blaze.fhir.spec-spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.node/resource-indexer
  (s/keys
    :req-un
    [:blaze.db/search-param-registry
     :blaze.db/kv-store]))


(s/fdef resource-indexer/index-resources
  :args (s/cat :resource-indexer :blaze.db.node/resource-indexer
               :last-updated inst?
               :entries (s/map-of :blaze.resource/hash (s/nilable :blaze/resource)))
  :ret ac/completable-future?)


(s/fdef resource-indexer/new-resource-indexer
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :kv-store :blaze.db/kv-store))
