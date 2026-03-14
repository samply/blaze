(ns blaze.terminology-service.local.graph.spec
  (:require
   [blaze.terminology-service.local :as-alias local]
   [clojure.spec.alpha :as s]))

(s/def ::local/graph
  map?)
