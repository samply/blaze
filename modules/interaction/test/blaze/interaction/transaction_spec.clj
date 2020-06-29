(ns blaze.interaction.transaction-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.async-comp-spec]
    [blaze.db.spec]
    [blaze.executors :refer [executor?]]
    [blaze.interaction.transaction :as transaction]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef transaction/handler
  :args (s/cat :node :blaze.db/node :executor executor?)
  :ret :ring/handler)
