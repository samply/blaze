(ns blaze.interaction.history.instance-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.history.instance :as instance]
    [blaze.interaction.history.util-spec]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef instance/handler
  :args (s/cat :node :blaze.db/node)
  :ret :ring/handler)
