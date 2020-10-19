(ns blaze.interaction.delete-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.db.spec]
    [blaze.interaction.delete :as delete]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef delete/handler
  :args (s/cat :node :blaze.db/node :executor ::delete/executor)
  :ret :ring/handler)
