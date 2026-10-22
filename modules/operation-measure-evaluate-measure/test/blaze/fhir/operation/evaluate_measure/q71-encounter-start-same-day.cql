library "q71-encounter-start-same-day"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

define Criterion:
  exists (from [Encounter] E
    where E.period.start same day as @2025-02-18)

define InInitialPopulation:
  Criterion
