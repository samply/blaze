(ns blaze.db.impl.index.system-stats-spec
  (:require
   [blaze.db.impl.index.spec]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.kv.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/fdef system-stats/seek-value
  :args (s/cat :snapshot :blaze.db.kv/snapshot :t :blaze.db/t)
  :ret (s/nilable :blaze.db.index/stats))

(s/fdef system-stats/index-entry
  :args (s/cat :t :blaze.db/t :value :blaze.db.index/stats)
  :ret :blaze.db.kv/put-entry)
