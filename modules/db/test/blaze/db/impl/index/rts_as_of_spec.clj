(ns blaze.db.impl.index.rts-as-of-spec
  (:require
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.rts-as-of :as rts]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash-spec]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef rts/index-entries
  :args (s/cat :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :t :blaze.db/t
               :hash :blaze.resource/hash
               :num-changes nat-int?
               :op keyword?)
  :ret (s/coll-of :blaze.db.kv/put-entry-w-cf))
