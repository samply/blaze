(ns blaze.db.impl.index.tx-error-spec
  (:require
    [blaze.db.impl.index.tx-error :as te]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef te/tx-error
  :args (s/cat :kv-store :blaze.db/kv-store :t :blaze.db/t)
  :ret (s/nilable ::anom/anomaly))


(s/fdef te/index-entry
  :args (s/cat :t :blaze.db/t :anomaly ::anom/anomaly)
  :ret :blaze.db.kv/put-entry)
