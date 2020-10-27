(ns blaze.interaction.create-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.async.comp-spec]
    [blaze.db.spec]
    [blaze.interaction.create :as create]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef create/handler
  :args (s/cat :node :blaze.db/node :executor ::create/executor)
  :ret :ring/handler)
