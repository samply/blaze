(ns blaze.interaction.history.system-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.history.system :as system]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]))


(s/fdef system/handler
  :args (s/cat :node :blaze.db/node))
