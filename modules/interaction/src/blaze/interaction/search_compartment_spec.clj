(ns blaze.interaction.search-compartment-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.db.spec]
    [blaze.interaction.search-compartment :as search-compartment]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]))


(s/fdef search-compartment/handler
  :args (s/cat :node :blaze.db/node))
