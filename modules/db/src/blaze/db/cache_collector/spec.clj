(ns blaze.db.cache-collector.spec
  (:require
    [blaze.db.cache-collector.protocols :as p]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.cache-collector/caches
  (s/map-of string? (s/nilable #(satisfies? p/StatsCache %))))
