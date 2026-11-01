(ns blaze.job.util.spec
  (:require
   [blaze.job.util.async-status-url :as-alias async-status-url]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/def :blaze.job.util/running-jobs
  (s/map-of string? (s/or :running false? :stop-reason #{:cancelled :paused :shutdown})))

(s/def ::async-status-url/context
  (s/keys :opt-un [:blaze/context-path]))

(s/def ::async-status-url/request
  (s/keys :req [:blaze/base-url]))
