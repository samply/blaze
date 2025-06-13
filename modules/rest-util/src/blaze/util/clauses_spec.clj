(ns blaze.util.clauses-spec
  (:require
   [blaze.handler.fhir.util.spec]
   [blaze.spec]
   [blaze.util.clauses :as uc]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef uc/clauses
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/or :clauses :blaze.db.query/clauses :anomaly ::anom/anomaly))

(s/fdef uc/search-clauses
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret :blaze.db.query/search-clauses)
