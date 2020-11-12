(ns blaze.fhir.hash.spec
  (:require
    [blaze.byte-string :as bs :refer [byte-string?]]
    [clojure.spec.alpha :as s]))


(s/def :blaze.resource/hash
  (s/and byte-string? #(= 32 (bs/size %))))
