(ns blaze.db.tx-log-spec
  (:require
    [blaze.async-comp :as ac]
    [blaze.async-comp-spec]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s])
  (:import
    [java.time Duration]))


;; returns a CompletableFuture of :blaze.db/t
(s/fdef tx-log/submit
  :args (s/cat :tx-log :blaze.db/tx-log :tx-cmds :blaze.db/tx-cmds)
  :ret ac/completable-future?)


(s/fdef tx-log/new-queue
  :args (s/cat :tx-log :blaze.db/tx-log :offset :blaze.db/t)
  :ret ::tx-log/queue)


(s/fdef tx-log/poll
  :args (s/cat :queue ::tx-log/queue :timeout #(instance? Duration %))
  :ret (s/nilable (s/coll-of :blaze.db/tx-data)))
