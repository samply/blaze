(ns blaze.job.compact.spec
  (:require
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.job.compact :as-alias compact]
   [clojure.spec.alpha :as s]))

(s/def ::compact/index-db
  :blaze.db/kv-store)

(s/def ::compact/transaction-db
  :blaze.db/kv-store)

(s/def ::compact/resource-db
  :blaze.db/kv-store)

(s/def ::compact/admin-node
  :blaze.db/node)
