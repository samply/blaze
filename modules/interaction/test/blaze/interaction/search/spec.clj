(ns blaze.interaction.search.spec
  (:require
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/def ::code
  string?)

(s/def ::target-type
  :fhir.resource/type)

(s/def ::include-def
  (s/keys :req-un [::code] :opt-un [::target-type]))

(s/def ::forward
  (s/map-of :fhir.resource/type (s/coll-of ::include-def)))

(s/def ::reverse
  (s/map-of (s/or :type :fhir.resource/type :any #{:any}) (s/coll-of ::include-def)))

(s/def ::direct
  (s/keys :req-un [(or ::forward ::reverse)]))

(s/def ::iterate
  (s/keys :req-un [(or ::forward ::reverse)]))

(s/def :blaze.interaction.search/include-defs
  (s/keys :req-un [::direct] :opt-un [::iterate]))
