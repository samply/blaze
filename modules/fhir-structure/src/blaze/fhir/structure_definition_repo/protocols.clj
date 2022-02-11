(ns blaze.fhir.structure-definition-repo.protocols)


(defprotocol StructureDefinitionRepo
  (-primitive-types [repo])
  (-complex-types [repo])
  (-resources [repo]))
