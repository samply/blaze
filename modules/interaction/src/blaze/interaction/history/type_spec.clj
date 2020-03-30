(ns blaze.interaction.history.type-spec
  (:require
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.history.type :as type]
    [blaze.interaction.history.util-spec]
    [clojure.spec.alpha :as s]))


(s/fdef type/handler
  :args (s/cat :node :blaze.db/node)
  :ret fn?)
