(ns blaze.db.impl.index.reverse-reference-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.reverse-reference :as rr]
    [blaze.db.kv-spec]
    [blaze.db.resource-store.spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef rr/source-tid-id
  :args (s/cat :rri :blaze.db/kv-iterator
               :dst-tid :blaze.db/tid
               :dst-id :blaze.db/id-byte-string)
  :ret (s/coll-of (s/tuple :blaze.db/tid :blaze.db/id-byte-string) :kind sequential?))
