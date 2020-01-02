(ns blaze.interaction.history.instance-spec
  (:require
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.history.instance :as instance]
    [blaze.interaction.history.util-spec]
    [clojure.spec.alpha :as s]))


(s/fdef instance/handler
  :args (s/cat :node :blaze.db/node)
  :ret fn?)
