(ns blaze.db.impl.index.compartment.resource-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string-spec]
   [blaze.coll.core-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.batch-db.spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index :as-alias index]
   [blaze.db.impl.index.compartment.resource :as cr]
   [blaze.db.impl.iterators-spec]
   [blaze.db.impl.search-param.spec]
   [blaze.db.kv.spec]
   [clojure.spec.alpha :as s]))

(s/fdef cr/resource-handles
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of ::index/handle))

(s/fdef cr/index-entry
  :args (s/cat :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :id :blaze.db/id-byte-string)
  :ret :blaze.db.kv/put-entry)
