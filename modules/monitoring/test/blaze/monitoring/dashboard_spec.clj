(ns blaze.monitoring.dashboard-spec
  (:require
   [blaze.monitoring.dashboard :as dashboard]
   [blaze.monitoring.dashboard.spec]
   [clojure.spec.alpha :as s]))

(s/fdef dashboard/dashboard
  :args (s/cat :source :blaze.monitoring/dashboard)
  :ret map?)
