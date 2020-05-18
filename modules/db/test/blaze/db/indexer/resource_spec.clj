(ns blaze.db.indexer.resource-spec
  (:require
    [blaze.db.impl.index-spec]
    [blaze.db.indexer-spec]
    [blaze.db.indexer.resource :as resource]
    [blaze.db.kv.spec]
    [blaze.db.search-param-registry-spec]
    [blaze.executors :as ex]
    [blaze.executors_spec]
    [clojure.spec.alpha :as s]))


(s/fdef resource/init-resource-indexer
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :kv-store :blaze.db/kv-store
               :executor ex/executor?))
