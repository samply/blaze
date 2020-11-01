(ns blaze.db.impl.search-param.util-spec
  (:require
    [blaze.db.impl.batch-db.spec]
    [blaze.db.impl.search-param.util :as u]
    [clojure.spec.alpha :as s]))


(s/fdef u/resource-hash
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes)
  :ret (s/nilable :blaze.resource/hash))
