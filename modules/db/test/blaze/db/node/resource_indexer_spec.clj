(ns blaze.db.node.resource-indexer-spec
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.byte-string-spec]
    [blaze.db.impl.index.compartment.resource-spec]
    [blaze.db.impl.search-param-spec]
    [blaze.db.kv-spec]
    [blaze.db.node.resource-indexer :as resource-indexer]
    [blaze.db.resource-store.spec]
    [blaze.db.search-param-registry.spec]
    [blaze.fhir.spec-spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.node/resource-indexer
  (s/keys
    :req-un
    [:blaze.db/resource-lookup
     :blaze.db/search-param-registry
     :blaze.db/kv-store]))


(s/fdef resource-indexer/index-resources
  :args (s/cat :resource-indexer :blaze.db.node/resource-indexer
               :hashes (s/coll-of :blaze.resource/hash))
  :ret ac/completable-future?)


(s/fdef resource-indexer/new-resource-indexer
  :args (s/cat :resource-lookup :blaze.db/resource-lookup
               :search-param-registry :blaze.db/search-param-registry
               :kv-store :blaze.db/kv-store
               :executor :blaze.db.node/resource-indexer-executor
               :batch-size :blaze.db.node/resource-indexer-batch-size))
