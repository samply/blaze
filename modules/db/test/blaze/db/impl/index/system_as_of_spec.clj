(ns blaze.db.impl.index.system-as-of-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.batch-db.spec]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index.resource-handle-spec]
   [blaze.db.impl.index.system-as-of :as sao]
   [blaze.db.impl.iterators-spec]
   [blaze.db.kv-spec]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]))

(s/fdef sao/system-history
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :start-t :blaze.db/t
               :start-tid (s/nilable :blaze.db/tid)
               :start-id (s/nilable :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))
