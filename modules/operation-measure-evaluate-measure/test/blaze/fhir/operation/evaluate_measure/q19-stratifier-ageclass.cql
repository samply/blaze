library "q19-stratifier-ageclass"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  true

define AgeClass:
  (AgeInYears() div 10) * 10
