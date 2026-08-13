(ns blaze.db.node.waiters-spec
  (:require
   [blaze.db.node.waiters :as waiters]
   [blaze.db.node.waiters.spec]
   [blaze.db.spec]
   [clojure.spec.alpha :as s]))

(s/fdef waiters/add
  :args (s/cat :waiters :blaze.db.node/waiters :t :blaze.db/t)
  :ret :blaze.db.node/waiters)

(s/fdef waiters/remove-ready
  :args (s/cat :waiters :blaze.db.node/waiters :t :blaze.db/t)
  :ret :blaze.db.node/waiters)

(s/fdef waiters/complete-ready!
  :args (s/cat :waiters :blaze.db.node/waiters :reached-t :blaze.db/t
               :t :blaze.db/t))

(s/fdef waiters/fail-all!
  :args (s/cat :waiters :blaze.db.node/waiters :error-fn ifn?))
