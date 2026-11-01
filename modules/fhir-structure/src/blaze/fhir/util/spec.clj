(ns blaze.fhir.util.spec
  (:require
   [blaze.fhir.util :as-alias fu]
   [blaze.fhir.util.param-spec :as-alias param-spec]
   [clojure.spec.alpha :as s]))

(s/def ::param-spec/action
  #{:copy :copy-complex-type :copy-resource})

(s/def ::param-spec/cardinality
  #{:many})

(s/def ::param-spec/coerce
  ifn?)

(s/def ::param-spec/key
  simple-keyword?)

(s/def ::param-spec/required
  boolean?)

;; A spec without :action declares a known but unsupported parameter.
(s/def ::fu/param-spec
  (s/keys :opt-un [::param-spec/action ::param-spec/cardinality
                   ::param-spec/coerce ::param-spec/key
                   ::param-spec/required]))

(s/def ::fu/param-specs
  (s/map-of string? ::fu/param-spec))
