(ns blaze.interaction.util-spec
  (:require
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [blaze.interaction.util :as u]
    [clojure.spec.alpha :as s]))


(s/fdef u/etag->t
  :args (s/cat :etag (s/nilable string?))
  :ret (s/nilable :blaze.db/t))


(s/fdef u/clauses
  :args (s/cat :query (s/nilable string?))
  :ret (s/coll-of :blaze.db.query/clause))
