library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

define InInitialPopulation:
  exists(
    from [Specimen] S
    where FHIRHelpers.ToDateTime(S.collection.collected) < @2006)
