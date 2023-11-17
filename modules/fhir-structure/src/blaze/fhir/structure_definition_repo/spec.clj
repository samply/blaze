(ns blaze.fhir.structure-definition-repo.spec
  (:require
   [blaze.fhir.structure-definition-repo.protocols :as p]
   [clojure.spec.alpha :as s]))

(defn structure-definition-repo? [x]
  (satisfies? p/StructureDefinitionRepo x))

(s/def :blaze.fhir/structure-definition-repo
  structure-definition-repo?)
