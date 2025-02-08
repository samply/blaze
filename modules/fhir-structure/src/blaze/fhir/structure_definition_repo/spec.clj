(ns blaze.fhir.structure-definition-repo.spec
  (:require
   [blaze.fhir.structure-definition-repo.protocols :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze.fhir/structure-definition-repo
  #(satisfies? p/StructureDefinitionRepo %))
