(ns blaze.fhir.search-clauses-spec
  (:require
   [blaze.fhir.search-clauses :as fsc]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef fsc/clauses
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/or :clauses :blaze.db.query/clauses :anomaly ::anom/anomaly))

(s/fdef fsc/search-clauses
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret :blaze.db.query/search-clauses)
