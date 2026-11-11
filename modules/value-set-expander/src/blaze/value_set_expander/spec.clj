(ns blaze.value-set-expander.spec
  (:require
   [blaze.value-set-expander.protocols :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze/value-set-expander
  #(satisfies? p/ValueSetExpander %))
