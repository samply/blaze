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
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef rao/encode-key
  :args (s/cat :tid :blaze.db/tid :id :blaze.db/id-byte-string :t :blaze.db/t)
  :ret bytes?)

(s/fdef rao/type-list
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef rao/system-list
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :start (s/? (s/cat :start-tid :blaze.db/tid
                                  :start-id :blaze.db/id-byte-string)))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef rao/instance-history
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :t :blaze.db/t
               :since-t :blaze.db/t
               :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :start-t :blaze.db/t)
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef rao/resource-handle
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :t :blaze.db/t
               :since-t :blaze.db/t
               :tid :blaze.db/tid
               :id :blaze.db/id-byte-string)
  :ret (s/nilable :blaze.db/resource-handle))

(s/fdef rao/resource-handle-xf
  :args (s/cat :batch-db :blaze.db.impl/batch-db)
  :ret fn?)

(s/fdef rao/resource-handle-type-xf
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :id-extractor (s/? fn?)
               :matcher (s/? fn?))
  :ret fn?)

(s/fdef rao/estimated-scan-size
  :args (s/cat :kv-store :blaze.db/kv-store
               :tid :blaze.db/tid)
  :ret (s/or :estimate-storage-size nat-int? :anomaly ::anom/anomaly))
