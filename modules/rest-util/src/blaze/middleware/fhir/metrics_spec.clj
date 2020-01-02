(ns blaze.middleware.fhir.metrics-spec
  (:require
    [blaze.middleware.fhir.metrics :as metrics]
    [clojure.spec.alpha :as s]))


(s/fdef metrics/wrap-observe-request-duration
  :args (s/cat :handler fn? :interaction-name (s/? string?)))
