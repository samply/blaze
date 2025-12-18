library "q65-in-code-system-gender"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem AdministrativeGender: 'http://hl7.org/fhir/administrative-gender'

context Patient

define InInitialPopulation:
  Patient.gender in AdministrativeGender
