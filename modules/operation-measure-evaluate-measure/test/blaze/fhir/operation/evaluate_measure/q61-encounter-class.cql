library "q61-encounter-class"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem foo: 'system-120749'

context Patient

define InInitialPopulation:
  exists (from [Encounter] E
    where E.class ~ Code 'code-120753' from foo)
