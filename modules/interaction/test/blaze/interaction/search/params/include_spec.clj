(ns blaze.interaction.search.params.include-spec
  (:require
    [blaze.db.spec]
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.search.params.include :as include]
    [blaze.interaction.search.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef include/include-defs
  :args (s/cat :handling (s/nilable (s/and keyword? #(= "blaze.preference.handling" (namespace %))))
               :query-params (s/nilable :ring.request/query-params))
  :ret (s/or :result (s/nilable :blaze.interaction.search/include-defs)
             :anomaly ::anom/anomaly))
