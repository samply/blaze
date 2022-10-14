library "q22-stratifier-multiple-cities-fail"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  true

define City:
  Patient.address.city
