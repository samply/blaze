(ns blaze.db.impl.db-spec
  (:require
    [blaze.db.api-spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.db :as db]
    [blaze.db.impl.index-spec]
    [blaze.db.kv-spec]
    [clojure.spec.alpha :as s]))


(s/fdef db/db
  :args (s/cat :kv-store :blaze.db/kv-store
               :resource-cache :blaze.db/resource-cache
               :node :blaze.db/node
               :t :blaze.db/t))
