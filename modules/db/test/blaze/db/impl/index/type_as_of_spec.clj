(ns blaze.db.impl.index.type-as-of-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.db.spec]
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
               :start-id (s/nilable :blaze.resource/id))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef tao/type-list-desc
  :args (s/cat :db :blaze.db.impl/db
               :tid :blaze.db/tid
               :since-t :blaze.db/t
               :start-t :blaze.db/t
               :start-id (s/nilable :blaze.resource/id))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef tao/type-list-asc
  :args (s/cat :db :blaze.db.impl/db
               :tid :blaze.db/tid
               :start (s/? (s/cat :start-t :blaze.db/t
                                  :start-id :blaze.resource/id)))
  :ret (cs/coll-of :blaze.db/resource-handle))
