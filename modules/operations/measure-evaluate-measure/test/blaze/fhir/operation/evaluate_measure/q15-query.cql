library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  AgeInYears() between 18 and 45
