(ns blaze.db.impl.index.resource-spec
  (:require
    [blaze.db.hash.spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource :as resource]
    [blaze.db.kv-spec]
    [blaze.db.resource-store.spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef resource/hash
  :args (s/cat :resource :blaze/resource)
  :ret :blaze.db.resource/hash)


(s/fdef resource/new-resource
  :args (s/cat :node :blaze.db/node
               :tid :blaze.db/tid
               :id string?
               :hash :blaze.db.resource/hash
               :state :blaze.db/state
               :t :blaze.db/t)
  :ret :blaze/resource)
