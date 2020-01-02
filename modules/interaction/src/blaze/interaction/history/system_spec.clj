(ns blaze.interaction.history.system-spec
  (:require
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.history.system :as system]
    [blaze.interaction.history.util-spec]
    [clojure.spec.alpha :as s]))


(s/fdef system/handler
  :args (s/cat :node :blaze.db/node)
  :ret fn?)
