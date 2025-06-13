(ns blaze.operation.graph.spec
  (:require
   [blaze.operation.graph :as-alias graph]
   [clojure.spec.alpha :as s])
  (:import
   [com.github.benmanes.caffeine.cache Cache]))

(s/def ::graph/compiled-graph-cache
  #(instance? Cache %))
