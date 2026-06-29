(ns blaze.validator.spec
  (:require
   [blaze.validator.protocols :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze/validator
  #(satisfies? p/Validator %))
