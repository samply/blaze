(ns blaze.interaction.search-compartment-spec
  (:require
    [blaze.async-comp-spec]
    [blaze.db.spec]
    [blaze.interaction.search-compartment :as search-compartment]
    [blaze.interaction.search.nav-spec]
    [blaze.interaction.search.params-spec]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef search-compartment/handler
  :args (s/cat :node :blaze.db/node)
  :ret :ring/handler)
