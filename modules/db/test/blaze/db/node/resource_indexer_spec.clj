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
   [blaze.db.node.resource-indexer.spec]
   [blaze.db.resource-store.spec]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec-spec]
   [clojure.spec.alpha :as s]))

(s/fdef resource-indexer/index-resources
  :args (s/cat :context :blaze.db.node/resource-indexer
               :tx-data :blaze.db/tx-data)
  :ret ac/completable-future?)
