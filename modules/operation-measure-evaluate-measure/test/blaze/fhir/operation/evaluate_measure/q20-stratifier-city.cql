library "q20-stratifier-city"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  true

define City:
  First(Patient.address.city)
