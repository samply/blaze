(ns blaze.fhir.spec.type.protocols)


(defprotocol FhirType
  (-type [_])
  (-interned [_])
  (-value [_])
  (-to-xml [_])
  (-hash-into [_ sink])
  (-references [_]))
