(ns blaze.job.prune.spec
  (:require
   [blaze.db.spec]
   [blaze.job.prune :as-alias prune]
   [clojure.spec.alpha :as s]))

(s/def ::prune/main-node
  :blaze.db/node)

(s/def ::prune/admin-node
  :blaze.db/node)

(s/def ::prune/index-entries-per-step
  pos-int?)
