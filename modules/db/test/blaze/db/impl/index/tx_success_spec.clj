(ns blaze.db.impl.index.tx-success-spec
  (:require
    [blaze.db.impl.index.tx-success :as tsi]
    [blaze.db.kv.spec]
    [blaze.db.spec]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]))


(s/fdef tsi/tx
  :args (s/cat :kv-store :blaze.db/kv-store :t :blaze.db/t)
  :ret (s/nilable :blaze.db/tx))


(s/fdef tsi/last-t
  :args (s/cat :kv-store :blaze.db/kv-store)
  :ret (s/nilable :blaze.db/t))


(s/fdef tsi/index-entry
  :args (s/cat :t :blaze.db/t :instant inst?)
  :ret :blaze.db.kv/put-entry)
