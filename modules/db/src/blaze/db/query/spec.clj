(ns blaze.db.query.spec
  (:require
   [blaze.db.query :as-alias query]
   [blaze.db.query.plan :as-alias plan]
   [blaze.db.query.plan.spec]
   [clojure.spec.alpha :as s]))

(s/def ::query/plan
  (s/keys :req-un [::plan/query-type ::plan/scan-type ::plan/scan-clauses
                   ::plan/seek-clauses]))
