library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

define InInitialPopulation:
  exists(
    from [Condition] C
    where FHIRHelpers.ToDateTime(C.onset) < @2006)
