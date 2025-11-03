(ns blaze.db.impl.index.resource-handle-spec
  (:require
   [blaze.byte-buffer :refer [byte-buffer?]]
   [blaze.byte-buffer-spec]
   [blaze.byte-string :refer [byte-string?]]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.kv-spec]
   [blaze.db.resource-store.spec]
   [blaze.db.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]))

(s/fdef rh/resource-handle!
  :args (s/cat :tid :blaze.db/tid :id :blaze.resource/id
               :t :blaze.db/t :base-t :blaze.db/t :vb byte-buffer?)
  :ret (s/nilable :blaze.db/resource-handle))

(s/fdef rh/resource-handle?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef rh/deleted?
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret boolean?)

(s/fdef rh/tid-id
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret byte-string?)
