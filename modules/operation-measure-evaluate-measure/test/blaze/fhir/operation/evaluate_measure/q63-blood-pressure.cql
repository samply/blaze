library "q63-blood-pressure"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define Criterion:
  exists (from [Observation: Code '85354-9' from loinc] O
    where O.component.where(code.coding.exists(system = 'http://loinc.org' and code = '8462-4')).value.ofType(Quantity) <= 80 'mm[Hg]')

define InInitialPopulation:
  Criterion
