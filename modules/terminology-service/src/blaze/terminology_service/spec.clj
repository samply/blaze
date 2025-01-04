(ns blaze.terminology-service.spec
  (:require
   [blaze.terminology-service.protocols :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze/terminology-service
  #(satisfies? p/TerminologyService %))
