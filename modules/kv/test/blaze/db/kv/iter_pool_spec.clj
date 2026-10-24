(ns blaze.db.kv.iter-pool-spec
  (:require
   [blaze.db.kv :as kv]
   [blaze.db.kv.iter-pool :as ip]
   [blaze.db.kv.spec]
   [clojure.spec.alpha :as s]))

(s/fdef ip/pooling-snapshot
  :args (s/cat :snapshot ::kv/snapshot)
  :ret ::kv/snapshot)
