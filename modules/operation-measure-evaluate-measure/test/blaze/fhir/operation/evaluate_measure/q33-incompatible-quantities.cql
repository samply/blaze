library "q33-incompatible-quantities"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

define InInitialPopulation:
  exists
    from [Observation: Code '4548-4' from loinc] O
    where (O.value as Quantity) > 9 'g/dL' or (O.value as Quantity) > 5.58 'mmol/L'
