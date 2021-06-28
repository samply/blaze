(ns blaze.interaction.history.type-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.history.type :as type]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]))


(s/fdef type/handler
  :args (s/cat :node :blaze.db/node))
