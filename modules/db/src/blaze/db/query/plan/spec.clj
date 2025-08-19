(ns blaze.db.query.plan.spec
  (:require
   [blaze.db.query.clause :as-alias query-clause]
   [blaze.db.query.plan :as-alias plan]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/def ::plan/query-type
  #{:type :compartment})

(s/def ::plan/scan-type
  #{:ordered :unordered})

(s/def ::plan/scan-clauses
  (s/keys :req-un [::query-clause/code]))

(s/def ::plan/seek-clauses
  (s/keys :req-un [::query-clause/code]))
