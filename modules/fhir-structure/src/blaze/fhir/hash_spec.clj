(ns blaze.fhir.hash-spec
  (:require
    [blaze.byte-string-spec]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef hash/generate
  :args (s/cat :resource :blaze/resource)
  :ret :blaze.resource/hash)
