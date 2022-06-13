(ns blaze.db.impl.index.type-as-of-spec
  (:require
    [blaze.byte-string-spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource-handle-spec]
    [blaze.db.impl.index.type-as-of :as tao]
    [blaze.db.impl.iterators-spec]
    [blaze.db.kv-spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef tao/type-history
  :args (s/cat :taoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :start-t :blaze.db/t
               :start-did (s/nilable :blaze.db/did)
               :end-t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))
