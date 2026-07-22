library "q70-period-without-end-overlaps"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

define Criterion:
  exists (from [MedicationStatement] M
    where M.effective overlaps Interval[@2020-01-01T, @2022-01-01T])

define InInitialPopulation:
  Criterion
