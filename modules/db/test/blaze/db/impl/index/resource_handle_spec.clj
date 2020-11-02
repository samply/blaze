(ns blaze.db.impl.index.resource-handle-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.kv-spec]
    [blaze.db.resource-store.spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef rh/resource-handle
  :args (s/cat :tid :blaze.db/tid :id :blaze.resource/id
               :t :blaze.db/t :hash :blaze.resource/hash
               :state :blaze.db/state)
  :ret :blaze.db/resource-handle)


(s/fdef rh/t
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret :blaze.db/t)


(s/fdef rh/num-changes
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret pos-int?)


(s/fdef rh/hash
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret :blaze.resource/hash)


(s/fdef rh/state
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret :blaze.db/state)


(s/fdef rh/resource-handle?
  :args (s/cat :x any?)
  :ret boolean?)


(s/fdef rh/deleted?
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret boolean?)
