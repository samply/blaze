(ns blaze.interaction.search-system-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.db.spec]
    [blaze.interaction.search-system :as search-system]
    [blaze.interaction.search.nav-spec]
    [blaze.interaction.search.params-spec]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]
    [ring.core.spec]))


(s/fdef search-system/handler
  :args (s/cat :node :blaze.db/node)
  :ret :ring/handler)
