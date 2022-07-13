library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define function IntegerParts(s String):
    from (Split(s, '-')) S where ConvertsToInteger(S)

define SocialSecurityNumber:
  First(Patient.identifier.where(system = 'http://hl7.org/fhir/sid/us-ssn').value)

define InInitialPopulation:
    Count(IntegerParts(SocialSecurityNumber)) = 3
