(ns blaze.bundle-spec
  (:require
    [blaze.bundle :as bundle]
    [blaze.db.api-spec]
    [blaze.fhir.spec-spec]
    [clojure.spec.alpha :as s]))


(s/fdef bundle/resolve-entry-links
  :args (s/cat :entries (s/coll-of map?)))


(s/fdef bundle/tx-ops
  :args (s/cat :entries (s/coll-of map? :min-count 1))
  :ret :blaze.db/tx-ops)
