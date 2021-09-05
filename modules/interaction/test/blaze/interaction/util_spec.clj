(ns blaze.interaction.util-spec
  (:require
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.util :as iu]
    [clojure.spec.alpha :as s]))


(s/fdef iu/etag->t
  :args (s/cat :etag (s/nilable string?))
  :ret (s/nilable :blaze.db/t))


(s/fdef iu/clauses
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/coll-of :blaze.db.query/clause))
