(ns blaze.interaction.search-type-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.db.spec]
    [blaze.interaction.search-type :as search-type]
    [blaze.interaction.search.nav-spec]
    [blaze.interaction.search.params-spec]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef search-type/handler
  :args (s/cat :node :blaze.db/node)
  :ret :ring/handler)
