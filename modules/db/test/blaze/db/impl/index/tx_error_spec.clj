(ns blaze.db.impl.index.tx-error-spec
  (:require
   [blaze.db.impl.index.tx-error :as tx-error]
   [blaze.db.kv.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef tx-error/tx-error
  :args (s/cat :kv-store :blaze.db/kv-store :t :blaze.db/t)
  :ret (s/nilable ::anom/anomaly))

(s/fdef tx-error/index-entry
  :args (s/cat :t :blaze.db/t :anomaly ::anom/anomaly)
  :ret :blaze.db.kv/put-entry)
