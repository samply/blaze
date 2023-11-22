(ns blaze.db.impl.db-spec
  (:require
   [blaze.db.api-spec]
   [blaze.db.impl.batch-db-spec]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.db :as db]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.index.patient-last-change-spec]
   [blaze.db.impl.index.system-stats-spec]
   [blaze.db.impl.index.type-stats-spec]
   [blaze.db.impl.search-param-spec]
   [blaze.db.kv-spec]
   [clojure.spec.alpha :as s]))

(s/fdef db/db
  :args (s/cat :node :blaze.db/node :t :blaze.db/t)
  :ret :blaze.db/db)
