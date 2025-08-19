(ns blaze.interaction.search.query-plan-spec
  (:require
   [blaze.db.spec]
   [blaze.interaction.search.query-plan :as query-plan]
   [clojure.spec.alpha :as s]))

(s/fdef query-plan/render
  :args (s/cat :query-plan :blaze.db.query/plan)
  :ret string?)
