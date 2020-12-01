(ns blaze.db.tx-log.local-spec
  (:require
    [blaze.async.comp-spec]
    [blaze.db.impl.iterators-spec]
    [blaze.db.kv.spec]
    [blaze.db.resource-store.spec]
    [blaze.db.spec]
    [blaze.db.tx-log.local :as tx-log]
    [blaze.db.tx-log.local.references-spec]
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s])
  (:import
    [java.time Clock]))


(s/fdef tx-log/new-local-tx-log
  :args (s/cat :kv-store :blaze.db/kv-store
               :clock #(instance? Clock %)
               :executor ex/executor?))
