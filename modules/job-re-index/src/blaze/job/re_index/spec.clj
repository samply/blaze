(ns blaze.job.re-index.spec
  (:require
   [blaze.db.spec]
   [blaze.job.re-index :as-alias re-index]
   [clojure.spec.alpha :as s]))

(s/def ::re-index/main-node
  :blaze.db/node)

(s/def ::re-index/admin-node
  :blaze.db/node)
