(ns blaze.db.db-spec
  (:require
    [blaze.db.api-spec]
    [blaze.db.db :as db]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index-spec]
    [blaze.db.search-param-registry-spec]
    [blaze.db.kv-spec]
    [clojure.spec.alpha :as s]))


(s/fdef db/db
  :args (s/cat :kv-store :blaze.db/kv-store
               :resource-cache :blaze.db/resource-cache
               :search-param-registry :blaze.db/search-param-registry
               :t :blaze.db/t))
