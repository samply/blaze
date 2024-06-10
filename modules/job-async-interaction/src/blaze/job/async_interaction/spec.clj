(ns blaze.job.async-interaction.spec
  (:require
   [blaze.db.spec]
   [blaze.job.async-interaction :as-alias async]
   [clojure.spec.alpha :as s]))

(s/def ::async/main-node
  :blaze.db/node)

(s/def ::async/admin-node
  :blaze.db/node)
