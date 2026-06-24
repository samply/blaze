library "q68-gender-parameter"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

parameter "Gender" String default 'male'

context Patient

define InInitialPopulation:
  Patient.gender = "Gender"
