(ns blaze.interaction.update-spec
  (:require
    [blaze.db.spec]
    [blaze.interaction.update :as update]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef update/handler
  :args (s/cat :node :blaze.db/node)
  :ret :ring/handler)
