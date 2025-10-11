(ns blaze.fhir.spec.type.protocols)

(defprotocol FhirType
  (-type [_])
  (-value [_])
  (-assoc-id [_ id])
  (-assoc-extension [_ extension])
  (-assoc-value [_ value])
  (-hash-into [_ sink])
  (-references [_]))
