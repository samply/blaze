(ns blaze.elm.code-system.spec
  (:require
   [blaze.elm.code-system.protocol :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze.elm/code-system
  #(satisfies? p/CodeSystem %))
