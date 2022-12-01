library "stratifier-birth-year"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  true

define BirthYear:
  year from Patient.birthDate
