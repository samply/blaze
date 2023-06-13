(ns blaze.rest-api.middleware.metrics-spec
  (:require
    [blaze.rest-api.middleware.metrics :as metrics]
    [clojure.spec.alpha :as s]))


(s/fdef metrics/wrap-observe-request-duration-fn
  :args (s/cat :interaction string?))
