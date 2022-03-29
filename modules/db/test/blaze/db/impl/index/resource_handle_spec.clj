(ns blaze.db.impl.index.resource-handle-spec
  (:require
    [blaze.byte-buffer :refer [byte-buffer?]]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.kv-spec]
    [blaze.db.resource-store.spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef rh/resource-handle
  :args (s/cat :tid :blaze.db/tid :id :blaze.resource/id
               :t :blaze.db/t :value-buffer byte-buffer?)
  :ret :blaze.db/resource-handle)


(s/fdef rh/resource-handle?
  :args (s/cat :x any?)
  :ret boolean?)
