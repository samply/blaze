(ns blaze.db.impl.index.plan-spec
  (:require
   [blaze.db.impl.batch-db.spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.index.plan :as plan]
   [blaze.db.index.query :as-alias query]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef plan/group-by-estimated-scan-size
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :search-clauses ::query/search-clauses)
  :ret (s/or :estimate-storage-size nat-int? :anomaly ::anom/anomaly))
