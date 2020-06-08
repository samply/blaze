(ns blaze.db.impl.index.resource-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource :as resource]
    [blaze.db.kv.spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef resource/hash
  :args (s/cat :resource :blaze/resource)
  :ret bytes?)


(s/fdef resource/new-resource
  :args (s/cat :node :blaze.db/node
               :tid :blaze.db/tid
               :id string?
               :hash bytes?
               :state :blaze.db/state
               :t :blaze.db/t)
  :ret :blaze/resource)
