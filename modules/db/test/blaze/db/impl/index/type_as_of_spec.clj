(ns blaze.db.impl.index.type-as-of-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource-handle-spec]
    [blaze.db.impl.index.type-as-of :as type-as-of]
    [blaze.db.impl.iterators-spec]
    [blaze.db.kv-spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef type-as-of/type-history
  :args (s/cat :taoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :start-t :blaze.db/t
               :start-id (s/nilable bytes?)
               :end-t :blaze.db/t)
  :ret (s/coll-of :blaze/resource))
