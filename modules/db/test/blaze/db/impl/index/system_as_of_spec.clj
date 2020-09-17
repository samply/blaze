(ns blaze.db.impl.index.system-as-of-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource-handle-spec]
    [blaze.db.impl.index.system-as-of :as system-as-of]
    [blaze.db.impl.iterators-spec]
    [blaze.db.kv-spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef system-as-of/system-history
  :args (s/cat :saoi :blaze.db/kv-iterator
               :start-t :blaze.db/t
               :start-tid (s/nilable :blaze.db/tid)
               :start-id (s/nilable bytes?)
               :end-t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))
