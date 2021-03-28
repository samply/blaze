(ns blaze.interaction.search-system-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.db.spec]
    [blaze.interaction.search-system :as search-system]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]))


(s/fdef search-system/handler
  :args (s/cat :node :blaze.db/node))
