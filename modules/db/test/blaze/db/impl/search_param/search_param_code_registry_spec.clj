(ns blaze.db.impl.search-param.search-param-code-registry-spec
  (:require
   [blaze.db.impl.search-param.search-param-code-registry :as search-param-code-registry]
   [blaze.db.impl.search-param.value-registry.spec]
   [blaze.db.kv.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef search-param-code-registry/id-of
  :args (s/cat :kv-store :blaze.db/kv-store
               :search-param-code (s/nilable string?))
  :ret (s/or :id :blaze.db/search-param-code-id :anomaly ::anom/anomaly))
