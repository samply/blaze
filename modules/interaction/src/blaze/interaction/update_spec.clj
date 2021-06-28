(ns blaze.interaction.update-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.async.comp-spec]
    [blaze.db.spec]
    [blaze.interaction.update :as update]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]))


(s/fdef update/handler
  :args (s/cat :node :blaze.db/node :executor ::update/executor))
