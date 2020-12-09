(ns blaze.db.impl.index.t-by-instant-spec
  (:require
    [blaze.db.impl.index.t-by-instant :as ti]
    [blaze.db.kv.spec]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]))


(s/fdef ti/t-by-instant
  :args (s/cat :snapshot :blaze.db/kv-snapshot :instant inst?)
  :ret (s/nilable :blaze.db/t))


(s/fdef ti/index-entry
  :args (s/cat :instant inst? :t :blaze.db/t)
  :ret :blaze.db.kv/put-entry)
