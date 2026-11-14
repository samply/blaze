(ns blaze.db.node.tx-indexer.util.spec
  (:require
   [blaze.db.impl.codec.spec]
   [blaze.db.node.tx-indexer.util :as-alias tx-u]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/def ::tx-u/rev-include-param
  (s/tuple :fhir.resource/type :blaze.db/tid :blaze.db/c-hash))
