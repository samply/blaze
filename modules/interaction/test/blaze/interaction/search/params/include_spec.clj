(ns blaze.interaction.search.params.include-spec
  (:require
    [blaze.db.spec]
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.search.params.include :as include]
    [blaze.interaction.search.spec]
    [clojure.spec.alpha :as s]))


(s/fdef include/include-defs
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :blaze.interaction.search/include-defs))
