(ns blaze.db.impl.batch-db.spec
  (:require
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.db.impl/batch-db
  (s/keys :req-un [:blaze.db/node ::kv/snapshot :blaze.db/t]))
