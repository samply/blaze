library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

define InInitialPopulation:
  exists
    from [Observation: Code '4548-4' from loinc] O
    where case
      when CanConvertQuantity(O.value, 'g/dL') then (O.value as Quantity) > 9 'g/dL'
      when CanConvertQuantity(O.value, 'mmol/L') then (O.value as Quantity) > 5.58 'mmol/L'
      else false
      end
