(ns blaze.job.compact.spec
  (:require
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.job.compact :as-alias job-compact]
   [clojure.spec.alpha :as s]))

(s/def ::job-compact/index-db
  :blaze.db/kv-store)

(s/def ::job-compact/transaction-db
  :blaze.db/kv-store)

(s/def ::job-compact/resource-db
  :blaze.db/kv-store)

(s/def ::job-compact/admin-node
  :blaze.db/node)

(s/def ::job-compact/database
  string?)

(s/def ::job-compact/column-family
  string?)

(s/def ::job-compact/params
  (s/keys :opt-un [::job-compact/database ::job-compact/column-family]))
