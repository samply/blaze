(ns blaze.db.impl.index.type-stats-spec
  (:require
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.spec]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.kv.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/fdef type-stats/new-iterator
  :args (s/cat :snapshot :blaze.db/kv-snapshot)
  :ret :blaze.db/kv-iterator)

(s/fdef type-stats/get!
  :args (s/cat :iter :blaze.db/kv-iterator :tid :blaze.db/tid :t :blaze.db/t)
  :ret (s/nilable :blaze.db.index/stats))

(s/fdef type-stats/index-entry
  :args (s/cat :tid :blaze.db/tid :t :blaze.db/t :value :blaze.db.index/stats)
  :ret :blaze.db.kv/put-entry)
