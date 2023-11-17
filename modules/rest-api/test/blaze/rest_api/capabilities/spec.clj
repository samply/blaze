(ns blaze.rest-api.capabilities.spec
  (:require
   [blaze.db.search-param-registry.spec]
   [blaze.rest-api.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.rest-api.capabilities/context
  (s/keys
   :req-un
   [:blaze/version
    :blaze.rest-api/structure-definitions
    :blaze.db/search-param-registry]
   :opt-un
   [:blaze/context-path
    :blaze.db/enforce-referential-integrity]))
