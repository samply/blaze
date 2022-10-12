library q2
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

define InInitialPopulation:
  exists
    from [Observation: Code {system: 'http://loinc.org', code: '39156-5'}] O
    where O.value as Quantity > 30 'kg/m2'
