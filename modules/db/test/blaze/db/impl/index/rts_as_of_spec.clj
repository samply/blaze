(ns blaze.db.impl.index.rts-as-of-spec
  (:require
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.rts-as-of :as rts]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash-spec]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef rts/index-entries
  :args (s/cat :tid :blaze.db/tid
               :id :blaze.resource/id
               :t :blaze.db/t
               :hash :blaze.resource/hash
               :num-changes nat-int?
               :op keyword?)
  :ret (s/coll-of :blaze.db.kv/put-entry))

(s/fdef rts/resource-handle
  :args (s/cat :tid :blaze.db/tid
               :id :blaze.resource/id
               :t :blaze.db/t
               :hash :blaze.resource/hash
               :num-changes nat-int?
               :op keyword?)
  :ret :blaze.db/resource-handle)

(s/fdef rts/purge-index-entries
  :args (s/and
         (s/cat :tid :blaze.db/tid
                :id :blaze.resource/id
                :resource-handle :blaze.db/resource-handle
                :purged-at :blaze.db/t)
         #(< (:t (:resource-handle %)) (:purged-at %)))
  :ret (s/coll-of :blaze.db.kv/put-entry))
