library q14
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  exists
    from [Condition] C
    where AgeInYearsAt(C.onset) between 18 and 45
