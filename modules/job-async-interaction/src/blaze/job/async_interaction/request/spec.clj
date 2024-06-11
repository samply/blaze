(ns blaze.job.async-interaction.request.spec
  (:require
   [blaze.job.async-interaction.request :as-alias req]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/def ::req/context
  (s/keys :req-un [:blaze/clock :blaze/rng-fn]
          :opt-un [:blaze/context-path]))
