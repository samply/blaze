(ns blaze.coerce-env-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.coerce-env :as ce]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef ce/validate-proxy-host
  :args (s/cat :host (s/nilable string?))
  :ret (s/or :host (s/nilable string?) :anomaly ::anom/anomaly))
