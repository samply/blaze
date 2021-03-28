(ns blaze.interaction.search-type-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.db.spec]
    [blaze.interaction.search-type :as search-type]
    [blaze.middleware.fhir.metrics-spec]
    [clojure.spec.alpha :as s]))


(s/fdef search-type/handler
  :args (s/cat :node :blaze.db/node))
