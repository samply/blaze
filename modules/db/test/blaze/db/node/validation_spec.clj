(ns blaze.db.node.validation-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.db.node.tx-indexer.util-spec]
   [blaze.db.node.validation :as validation]
   [blaze.db.search-param-registry.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef validation/validate-tx-cmds
  :args (s/cat :tx-cmds :blaze.db/tx-cmds)
  :ret (s/nilable ::anom/anomaly))

(s/fdef validation/validate-value-sets
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :tx-cmds :blaze.db/tx-cmds)
  :ret (s/nilable ::anom/anomaly))
