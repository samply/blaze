(ns blaze.handler.health.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze/health-handler
  fn?)
