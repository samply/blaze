(ns blaze.fhir.spec.type.protocols)


(defprotocol FhirType
  (-type [_])
  (-interned [_])
  (-value [_])
  (-serialize-json-as-field [_ field-name generator provider])
  (-serialize-json [_ generator provider])
  (-to-xml [_])
  (-hash-into [_ sink])
  (-references [_]))
