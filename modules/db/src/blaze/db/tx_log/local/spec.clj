(ns blaze.db.tx-log.local.spec
  (:require
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.db.tx-log.local/tx-logs
  (s/map-of some? :blaze.db/tx-log))
