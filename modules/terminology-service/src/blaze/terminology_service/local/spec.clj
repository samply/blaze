(ns blaze.terminology-service.local.spec
  (:require
   [blaze.fhir.spec]
   [blaze.path.spec]
   [blaze.terminology-service.local :as-alias local]
   [clojure.spec.alpha :as s])
  (:import
   [com.github.benmanes.caffeine.cache Cache]))

(s/def ::local/graph-cache
  #(instance? Cache %))

(s/def ::local/enable-bcp-13
  boolean?)

(s/def ::local/enable-bcp-47
  boolean?)

(s/def ::local/enable-ucum
  boolean?)

(s/def ::local/loinc
  map?)

(s/def ::local/sct
  map?)

(s/def ::local/num-concepts
  nat-int?)

(s/def ::local/tx-resource
  (s/or :code-system :fhir/CodeSystem :value-set :fhir/ValueSet))

(s/def ::local/tx-resources
  (s/coll-of ::local/tx-resource))
