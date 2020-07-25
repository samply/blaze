library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
    true

define Bmi:
  Round(Avg(from [Observation: Code '39156-5' from loinc] O
    return FHIRHelpers.ToDecimal((O.value as Quantity).value)))
