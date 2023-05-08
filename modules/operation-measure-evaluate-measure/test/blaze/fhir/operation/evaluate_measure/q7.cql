library q7
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

define InInitialPopulation:
  exists
    from [Condition] C
    where C.onset < @2006
