(ns blaze.interaction.search.spec
  (:require
    [blaze.fhir.spec.spec]
    [clojure.spec.alpha :as s]))


(s/def ::code
  string?)


(s/def ::target-type
  :fhir.type/name)


(s/def ::include-def
  (s/keys :req-un [::code] :opt-un [::target-type]))


(s/def ::direct
  (s/map-of :fhir.type/name ::include-def))


(s/def ::iterate
  (s/map-of :fhir.type/name ::include-def))


(s/def :blaze.interaction.search/include-defs
  (s/keys :req-un [::direct] :opt-un [::iterate]))
