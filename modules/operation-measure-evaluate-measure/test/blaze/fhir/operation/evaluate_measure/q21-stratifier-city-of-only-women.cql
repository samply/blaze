library "q21-stratifier-city-of-only-women"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  Patient.gender = 'female'

define City:
  First(Patient.address.city)
