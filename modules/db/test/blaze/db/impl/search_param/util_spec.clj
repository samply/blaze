(ns blaze.db.impl.search-param.util-spec
  (:require
    [blaze.db.impl.batch-db.spec]
    [blaze.db.impl.byte-string :refer [byte-string?]]
    [blaze.db.impl.codec.spec]
    [blaze.db.impl.search-param.util :as u]
    [blaze.db.kv.spec]
    [blaze.db.spec]
    [clojure.spec.alpha :as s]))


(s/fdef u/resource-handle-mapper
  :args (s/cat :context :blaze.db.impl.batch-db/context :tid :blaze.db/tid))


(s/fdef u/resource-sp-value-seek
  :args (s/cat :iter :blaze.db/kv-iterator
               :resource-handle :blaze.db/resource-handle
               :c-hash :blaze.db/c-hash
               :value (s/? byte-string?))
  :ret (s/nilable bytes?))


(s/fdef u/get-next-value
  :args (s/cat :iter :blaze.db/kv-iterator
               :resource-handle :blaze.db/resource-handle
               :c-hash :blaze.db/c-hash
               :prefix (s/? byte-string?))
  :ret (s/nilable byte-string?))
