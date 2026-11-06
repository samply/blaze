(ns blaze.fhir.test-util.graph-definition.spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.fhir.test-util.graph-definition :as-alias g-tu]
   [clojure.spec.alpha :as s]))

(s/def ::g-tu/value
  some?)

(s/def ::g-tu/extension
  (s/coll-of :fhir/Extension :kind vector?))

(s/def ::g-tu/start
  (s/nilable string?))

(s/def ::g-tu/nodes
  (s/coll-of :fhir/Extension :kind vector?))

(s/def ::g-tu/link
  (s/coll-of :fhir.GraphDefinition/link :kind vector?))

(s/def ::g-tu/profile
  string?)

(s/def ::g-tu/params
  string?)

(s/def ::g-tu/path
  string?)

(s/def ::g-tu/compartment
  boolean?)

(s/def ::extension-args
  (s/cat :kvs (s/keys* :opt-un [::g-tu/value ::g-tu/extension])))
