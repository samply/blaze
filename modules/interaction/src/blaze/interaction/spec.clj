(ns blaze.interaction.spec
  (:require
    [blaze.spec]
    [clojure.spec.alpha :as s]))


(s/def :fhir.router.match/data
  (s/keys :req [:blaze/base-url :blaze/context-path]))


(s/def :fhir.router/match
  (s/keys :req-un [:fhir.router.match/data]))
