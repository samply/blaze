(ns blaze.db.node.tx-indexer.util-spec
  (:require
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.util.spec]
   [clojure.spec.alpha :as s]))

(s/fdef tx-u/rev-include-param
  :args (s/cat :type :fhir.resource/type :code string?)
  :ret ::tx-u/rev-include-param)
