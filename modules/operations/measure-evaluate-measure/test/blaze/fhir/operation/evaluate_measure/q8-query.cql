library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Specimen

define InInitialPopulation:
  exists(from [Patient] P where P.gender = 'male')
