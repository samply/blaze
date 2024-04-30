(ns blaze.db.impl.search-param.system-registry-spec
  (:require
   [blaze.db.impl.search-param.system-registry :as system-registry]
   [blaze.db.impl.search-param.value-registry.spec]
   [blaze.db.kv.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef system-registry/id-of
  :args (s/cat :kv-store :blaze.db/kv-store
               :system (s/nilable string?))
  :ret (s/or :id :blaze.db/system-id :anomaly ::anom/anomaly))
