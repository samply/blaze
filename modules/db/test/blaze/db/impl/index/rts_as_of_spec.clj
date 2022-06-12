(ns blaze.db.impl.index.rts-as-of-spec
  (:require
    [blaze.db.impl.codec.spec]
    [blaze.db.impl.index.resource-as-of-spec]
    [blaze.db.impl.index.rts-as-of :as rts]
    [blaze.db.tx-log.spec]
    [blaze.fhir.hash-spec]
    [blaze.fhir.hash.spec]
    [blaze.fhir.spec.spec]
    [clojure.spec.alpha :as s]))


(s/fdef rts/encode-value
  :args (s/cat :hash :blaze.resource/hash
               :num-changes nat-int?
               :op keyword?
               :id :blaze.resource/id)
  :ret bytes?)


(s/fdef rts/index-entries
  :args (s/cat :tid :blaze.db/tid
               :did :blaze.db/did
               :t :blaze.db/t
               :hash :blaze.resource/hash
               :num-changes nat-int?
               :op keyword?
               :id :blaze.resource/id)
  :ret bytes?)
