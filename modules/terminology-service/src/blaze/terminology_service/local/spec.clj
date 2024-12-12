(ns blaze.terminology-service.local.spec
  (:require
   [blaze.terminology-service.local :as-alias local]
   [clojure.spec.alpha :as s]))

(s/def ::local/sct-release-path
  string?)
