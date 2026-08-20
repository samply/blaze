(ns blaze.db.node.stats-spec
  (:require
   [blaze.db.kv.spec]
   [blaze.db.node.stats :as stats]
   [blaze.db.node.stats.spec]
   [blaze.db.spec]
   [clojure.spec.alpha :as s]))

(s/fdef stats/init
  :args (s/cat :kv-store :blaze.db/kv-store :t :blaze.db/t)
  :ret ::stats/stats)

(s/fdef stats/apply-tx
  :args (s/cat :stats ::stats/stats :t :blaze.db/t
               :increments (s/nilable ::stats/increments))
  :ret (s/tuple (s/coll-of :blaze.db.kv/put-entry) ::stats/stats))
