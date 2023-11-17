(ns blaze.db.impl.index.tx-success-spec
  (:require
   [blaze.db.impl.index.tx-success :as tx-success]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/fdef tx-success/tx
  :args (s/cat :tx-cache :blaze.db/tx-cache :t :blaze.db/t)
  :ret (s/nilable :blaze.db/tx))

(s/fdef tx-success/last-t
  :args (s/cat :kv-store :blaze.db/kv-store)
  :ret (s/nilable :blaze.db/t))

(s/fdef tx-success/index-entry
  :args (s/cat :t :blaze.db/t :instant :blaze.db.tx/instant)
  :ret :blaze.db.kv/put-entry)
