(ns blaze.db.impl.index.resource-id-spec
  (:require
    [blaze.db.impl.codec.spec]
    [blaze.db.impl.index.resource-id :as ri]
    [blaze.db.kv.spec]
    [blaze.fhir.spec.spec]
    [clojure.spec.alpha :as s]))


(s/fdef ri/resource-id
  :args (s/cat :kv-store :blaze.db/kv-store)
  :ret :blaze.db/did)


(s/fdef ri/index-entry
  :args (s/cat :tid :blaze.db/tid :id :blaze.resource/id :did :blaze.db/did)
  :ret bytes?)
