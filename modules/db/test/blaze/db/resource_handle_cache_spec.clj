(ns blaze.db.resource-handle-cache-spec
  (:require
   [blaze.db.impl.cache-spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.node.spec]
   [blaze.db.resource-handle-cache :as rhc]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]))

(s/fdef rhc/resource-handle-cache?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef rhc/get
  :args (s/cat :cache :blaze.db/resource-handle-cache :tid :blaze.db/tid
               :id :blaze.resource/id :t :blaze.db/t)
  :ret (s/nilable :blaze.db/resource-handle))

(s/fdef rhc/put!
  :args (s/cat :cache :blaze.db/resource-handle-cache :t :blaze.db/t
               :handle :blaze.db/resource-handle)
  :ret nil?)

(s/fdef rhc/read-only
  :args (s/cat :cache :blaze.db/resource-handle-cache)
  :ret :blaze.db/resource-handle-cache)

(s/fdef rhc/set-update-t!
  :args (s/cat :cache :blaze.db/resource-handle-cache :t :blaze.db/t)
  :ret nil?)

(s/fdef rhc/update!
  :args (s/cat :cache :blaze.db/resource-handle-cache :t :blaze.db/t
               :updates (s/coll-of (s/tuple :blaze.db/resource-handle boolean?)))
  :ret nil?)

(s/fdef rhc/weight
  :args (s/cat :handle :blaze.db/resource-handle)
  :ret nat-int?)

(s/fdef rhc/max-weight
  :args (s/cat :cache :blaze.db/resource-handle-cache)
  :ret nat-int?)
