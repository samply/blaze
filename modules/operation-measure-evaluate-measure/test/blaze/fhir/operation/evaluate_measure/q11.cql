library q11
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

define InInitialPopulation:
  exists
    from [Specimen] S
    where S.collection.collected < @2006
