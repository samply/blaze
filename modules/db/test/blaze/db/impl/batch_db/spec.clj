(ns blaze.db.impl.batch-db.spec
  (:require
   [blaze.db.impl.batch-db :as-alias batch-db]
   [blaze.db.impl.index.resource-as-of :as-alias rao]
   [blaze.db.impl.index.resource-as-of.spec]
   [blaze.db.impl.index.resource-search-param-value :as-alias r-sp-v]
   [blaze.db.impl.index.resource-search-param-value.spec]
   [blaze.db.kv.spec]
   [clojure.spec.alpha :as s]))

(s/def ::batch-db/snapshot
  :blaze.db/kv-snapshot)

(s/def ::batch-db/context
  (s/keys :req-un [::batch-db/snapshot ::rao/resource-handle
                   ::r-sp-v/next-value ::r-sp-v/next-value-prev]))
