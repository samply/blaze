(ns blaze.db.impl.index.type-as-of-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index.resource-handle-spec]
   [blaze.db.impl.index.type-as-of :as tao]
   [blaze.db.impl.iterators-spec]
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv-spec]
   [blaze.db.kv.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]))

(s/fdef tao/type-history
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :tid :blaze.db/tid
               :t :blaze.db/t
               :start-t :blaze.db/t
               :start-id (s/nilable :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef tao/prune
  :args (s/cat :snapshot ::kv/snapshot
               :n pos-int?
               :t :blaze.db/t
               :start (s/? (s/cat :start-tid :blaze.db/tid
                                  :start-t :blaze.db/t
                                  :start-id :blaze.db/id-byte-string)))
  :ret (s/coll-of ::kv/delete-entry :kind vector?))
