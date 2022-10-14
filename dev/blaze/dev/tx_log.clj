(ns blaze.dev.tx-log
  (:require
    [blaze.core :refer [system]]
    [blaze.db.tx-log :as tx-log]
    [java-time.api :as time]))


(def tx-log
  (:blaze.db.tx-log/local system))


(comment
  @(tx-log/last-t tx-log)

  (with-open [q (tx-log/new-queue tx-log 12588)]
    (tx-log/poll! q (time/seconds 10)))
  )
