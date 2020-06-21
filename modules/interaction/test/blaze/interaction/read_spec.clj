(ns blaze.interaction.read-spec
  (:require
    [blaze.db.spec]
    [blaze.interaction.read :as read]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef read/handler
  :args (s/cat :node :blaze.db/node)
  :ret :ring/handler)
