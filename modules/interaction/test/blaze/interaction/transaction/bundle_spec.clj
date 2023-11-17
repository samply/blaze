(ns blaze.interaction.transaction.bundle-spec
  (:require
   [blaze.db.spec]
   [blaze.interaction.transaction.bundle :as bundle]
   [blaze.interaction.util-spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef bundle/assoc-tx-ops
  :args (s/cat :db :blaze.db/db :entries (s/coll-of map? :min-count 1))
  :ret (s/or :entries (s/coll-of map? :min-count 1) :anomaly ::anom/anomaly))

(s/fdef bundle/tx-ops
  :args (s/cat :entries (s/coll-of map? :min-count 1))
  :ret (s/coll-of :blaze.db/tx-op :kind vector?))
