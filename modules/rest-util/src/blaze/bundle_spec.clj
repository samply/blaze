(ns blaze.bundle-spec
  (:require
    [blaze.bundle :as bundle]
    [blaze.db.api-spec]
    [clojure.spec.alpha :as s]))


(s/fdef bundle/resolve-entry-links
  :args (s/cat :entries coll?))


(s/fdef bundle/tx-ops
  :args (s/cat :entries coll?)
  :ret :blaze.db/tx-ops)
