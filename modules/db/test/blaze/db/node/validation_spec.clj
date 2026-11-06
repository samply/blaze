(ns blaze.db.node.validation-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.db.node.validation :as validation]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef validation/validate-tx-cmds
  :args (s/cat :tx-cmds :blaze.db/tx-cmds)
  :ret (s/nilable ::anom/anomaly))
