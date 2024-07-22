(ns blaze.db.impl.index.resource-as-of-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.batch-db.spec]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index.resource-as-of :as rao]
   [blaze.db.impl.index.resource-handle-spec]
   [blaze.db.impl.iterators-spec]
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]))

(s/fdef rao/encode-key
  :args (s/cat :tid :blaze.db/tid :id :blaze.db/id-byte-string :t :blaze.db/t)
  :ret bytes?)

(s/fdef rao/type-list
  :args (s/cat :context (s/keys :req-un [::kv/snapshot :blaze.db/t])
               :tid :blaze.db/tid
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef rao/system-list
  :args (s/cat :context (s/keys :req-un [::kv/snapshot :blaze.db/t])
               :start (s/? (s/cat :start-tid :blaze.db/tid
                                  :start-id :blaze.db/id-byte-string)))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef rao/instance-history
  :args (s/cat :snapshot ::kv/snapshot
               :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :start-t :blaze.db/t)
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef rao/resource-handle
  :args (s/cat :snapshot ::kv/snapshot
               :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :t :blaze.db/t)
  :ret (s/nilable :blaze.db/resource-handle))

(s/fdef rao/resource-handle-xf
  :args (s/cat :snapshot ::kv/snapshot
               :t :blaze.db/t)
  :ret fn?)

(s/fdef rao/resource-handle-type-xf
  :args (s/cat :snapshot ::kv/snapshot
               :t :blaze.db/t
               :tid :blaze.db/tid
               :id-extractor (s/? fn?)
               :matcher (s/? fn?))
  :ret fn?)

(s/fdef rao/num-of-instance-changes
  :args (s/cat :snapshot ::kv/snapshot
               :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :start-t :blaze.db/t
               :end-t :blaze.db/t)
  :ret nat-int?)
