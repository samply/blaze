(ns blaze.validator.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze/validator
  map?)

(s/def :blaze.validator/terminology-service-base-url
  string?)
