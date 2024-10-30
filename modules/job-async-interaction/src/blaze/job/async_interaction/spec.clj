(ns blaze.job.async-interaction.spec
  (:require
   [blaze.db.spec]
   [blaze.job.async-interaction :as-alias job-async]
   [clojure.spec.alpha :as s]))

(s/def ::job-async/main-node
  :blaze.db/node)

(s/def ::job-async/admin-node
  :blaze.db/node)
