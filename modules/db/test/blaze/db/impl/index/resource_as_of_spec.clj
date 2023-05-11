(ns blaze.db.impl.index.resource-as-of-spec
  (:require
    [blaze.byte-string-spec]
    [blaze.db.impl.batch-db.spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.db.impl.index.resource-handle-spec]
    [blaze.db.impl.iterators-spec]
    [blaze.db.kv-spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef rao/encode-key
  :args (s/cat :tid :blaze.db/tid :id :blaze.db/id-byte-string :t :blaze.db/t)
  :ret bytes?)


(s/fdef rao/type-list
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef rao/system-list
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :start (s/? (s/cat :start-tid :blaze.db/tid
                                  :start-id :blaze.db/id-byte-string)))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef rao/instance-history
  :args (s/cat :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :start-t :blaze.db/t
               :end-t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/def ::resource-handle-fn
  (s/fspec
    :args
    (s/cat
      :tid :blaze.db/tid
      :id :blaze.db/id-byte-string
      :t (s/? :blaze.db/t))
    :ret
    (s/nilable :blaze.db/resource-handle)))


(s/fdef rao/caching-resource-handle
  :args (s/cat :rh-cache :blaze.db/resource-handle-cache
               :raoi :blaze.db/kv-iterator
               :t :blaze.db/t)
  :ret ::resource-handle-fn)


(s/fdef rao/non-caching-resource-handle
  :args (s/cat :raoi :blaze.db/kv-iterator
               :t :blaze.db/t)
  :ret ::resource-handle-fn)


(s/fdef rao/num-of-instance-changes
  :args (s/cat :resource-handle ::resource-handle-fn
               :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :start-t :blaze.db/t
               :end-t :blaze.db/t)
  :ret nat-int?)
