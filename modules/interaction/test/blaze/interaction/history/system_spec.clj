(ns blaze.interaction.history.system-spec
  (:require
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.history.system :as system]
    [blaze.interaction.history.util-spec]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef system/handler
  :args (s/cat :node :blaze.db/node)
  :ret :ring/handler)
