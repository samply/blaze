(ns blaze.fhir.spec.type.protocols)

(defprotocol FhirType
  (-type [_])
  (-value [_])
  (-hash-into [_ sink])
  (-references [_]))
