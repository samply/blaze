(ns blaze.db.tx-log-spec
  (:require
    [blaze.db.tx-log :as tx-log]
    [clojure.spec.alpha :as s]
    [manifold.deferred :refer [deferred?]]
    [manifold.stream :refer [stream?]]))


(s/fdef tx-log/submit
  :args (s/cat :tx-log :blaze.db/tx-log :tx-ops :blaze.db/tx-ops)
  :ret deferred?)


(s/fdef tx-log/log-queue
  :args (s/cat :tx-log :blaze.db/tx-log :from-t :blaze.db/t)
  :ret stream?)
