library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

define InInitialPopulation:
  exists
    from [Condition] C
    where year from C.onset between 2005 and 2006
