library "q52-sort-with-missing-values"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  true

define PrimaryDiagnosis:
  First(from [Condition] C sort by date from onset asc)
