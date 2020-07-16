(ns blaze.db.impl.index.type-stats-spec
  (:require
    [blaze.db.impl.index.type-stats :as type-stats]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.type-stats/total
  nat-int?)


(s/def :blaze.db.type-stats/num-changes
  nat-int?)


(s/def :blaze.db/type-stats
  (s/keys :req-un [:blaze.db.type-stats/total :blaze.db.type-stats/num-changes]))


(s/fdef type-stats/entry
  :args (s/cat :tid :blaze.db/tid :t :blaze.db/t :value :blaze.db/type-stats)
  :ret (s/tuple keyword? bytes? bytes?))


(s/fdef type-stats/get!
  :args (s/cat :iter :blaze.db/kv-iterator :tid :blaze.db/tid :t :blaze.db/t)
  :ret (s/nilable :blaze.db/type-stats))
