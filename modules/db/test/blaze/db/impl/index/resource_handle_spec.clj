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
               :t :blaze.db/t :vb byte-buffer?)
  :ret :blaze.db/resource-handle)

(s/fdef rh/resource-handle?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef rh/deleted?
  :args (s/cat :rh rh/resource-handle?)
  :ret boolean?)

(s/fdef rh/tid
  :args (s/cat :rh rh/resource-handle?)
  :ret :blaze.db/tid)

(s/fdef rh/id
  :args (s/cat :rh rh/resource-handle?)
  :ret :blaze.resource/id)

(s/fdef rh/t
  :args (s/cat :rh rh/resource-handle?)
  :ret :blaze.db/t)

(s/fdef rh/hash
  :args (s/cat :rh rh/resource-handle?)
  :ret :blaze.resource/hash)

(s/fdef rh/num-changes
  :args (s/cat :rh rh/resource-handle?)
  :ret :blaze.db/num-changes)

(s/fdef rh/op
  :args (s/cat :rh rh/resource-handle?)
  :ret :blaze.db/op)

(s/fdef rh/reference
  :args (s/cat :rh rh/resource-handle?)
  :ret :blaze.fhir/literal-ref)

(s/fdef rh/local-ref-tuple
  :args (s/cat :rh rh/resource-handle?)
  :ret :blaze.fhir/literal-ref-tuple)

(s/fdef rh/tid-id
  :args (s/cat :rh rh/resource-handle?)
  :ret byte-string?)
