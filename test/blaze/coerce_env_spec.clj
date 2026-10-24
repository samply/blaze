(ns blaze.coerce-env-spec
  (:require
   [blaze.coerce-env :as ce]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(set! *warn-on-reflection* true)

(s/fdef ce/coerce-cache-size-ratio
  :args (s/cat :s string?)
  :ret (s/or :ratio (s/and double? #(Double/isFinite %) (complement neg?))
             :anomaly ::anom/anomaly))

(s/fdef ce/limit-cache-size-ratios
  :args (s/cat :bind-map (s/map-of symbol? any?))
  :ret (s/map-of symbol? any?))
