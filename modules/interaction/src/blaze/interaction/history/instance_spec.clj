(ns blaze.interaction.history.instance-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.history.instance :as instance]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]))


(s/fdef instance/handler
  :args (s/cat :node :blaze.db/node))
