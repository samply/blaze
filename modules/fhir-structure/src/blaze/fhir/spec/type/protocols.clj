(ns blaze.fhir.spec.type.protocols)


(defprotocol FhirType
  (-type [_])
  (-interned [_])
  (-value [_])
  (-has-primary-content [value]
    "Returns true if there is primary JSON content available.")
  (-serialize-json [value generator]
    "Serializes the primary content of `value`.

    For primitive types, the primary content is the (inner) :value which can be
    represented as a primary JSON value or the JSON `null` value because that is
    needed to fill up JSON arrays in case `value` has secondary content. For all
    other types, it's the whole content.

    See also: https://www.hl7.org/fhir/datatypes.html#representations")
  (-has-secondary-content [value]
    "Returns true if there is secondary JSON content available.")
  (-serialize-json-secondary [value generator]
    "Serializes the secondary content of `value`.

    For primitive types, the secondary content is it's :id and :extension that
    has to be serialized under a field starting with an underscore. For all
    other types, it's a JSON `null` value because that is needed to fill up
    JSON arrays in case `value` has primary content.

    See also: https://www.hl7.org/fhir/datatypes.html#representations")
  (-to-xml [_])
  (-hash-into [_ sink])
  (-references [_]))
