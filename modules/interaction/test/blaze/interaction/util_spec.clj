(ns blaze.interaction.util-spec
  (:require
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.util :as iu]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef iu/etag->t
  :args (s/cat :etag string?)
  :ret (s/nilable :blaze.db/t))


(s/fdef iu/clauses
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/or :clauses :blaze.db.query/clauses :anomaly ::anom/anomaly))


(s/fdef iu/search-clauses
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret :blaze.db.query/search-clauses)
