(ns blaze.db.impl.index.type-as-of-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.batch-db.spec]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index.resource-handle-spec]
   [blaze.db.impl.index.type-as-of :as tao]
   [blaze.db.impl.iterators-spec]
   [blaze.db.kv-spec]
   [blaze.db.kv.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]))

(s/fdef tao/type-history
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :t :blaze.db/t
               :since-t :blaze.db/t
               :tid :blaze.db/tid
               :start-t :blaze.db/t
               :start-id (s/nilable :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))
