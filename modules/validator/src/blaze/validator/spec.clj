(ns blaze.validator.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze/validator
  map?)
