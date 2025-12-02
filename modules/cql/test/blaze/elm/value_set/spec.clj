(ns blaze.elm.value-set.spec
  (:require
   [blaze.elm.value-set.protocol :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze.elm/value-set
  #(satisfies? p/ValueSet %))
