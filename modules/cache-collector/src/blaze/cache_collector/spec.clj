(ns blaze.cache-collector.spec
  (:require
   [blaze.cache-collector.protocols :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze.cache-collector/caches
  (s/map-of string? (s/nilable #(satisfies? p/StatsCache %))))
