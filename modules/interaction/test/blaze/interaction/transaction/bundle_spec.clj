(ns blaze.interaction.transaction.bundle-spec
  (:require
    [blaze.db.spec]
    [blaze.interaction.transaction.bundle :as bundle]
    [clojure.spec.alpha :as s]))


(s/fdef bundle/tx-ops
  :args (s/cat :entries (s/coll-of map? :min-count 1))
  :ret :blaze.db/tx-ops)
