(ns blaze.db.node.validation-spec
  (:require
    [blaze.db.node.validation :as validation]
    [blaze.db.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef validation/validate-ops
  :args (s/cat :tx-ops :blaze.db/tx-ops)
  :ret (s/nilable ::anom/anomaly))
