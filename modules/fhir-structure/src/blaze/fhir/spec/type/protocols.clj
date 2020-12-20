(ns blaze.fhir.spec.type.protocols)


(defprotocol FhirType
  (-type [_])
  (-value [_])
  (-to-json [_])
  (-to-xml [_])
  (-hash-into [_ sink]))
