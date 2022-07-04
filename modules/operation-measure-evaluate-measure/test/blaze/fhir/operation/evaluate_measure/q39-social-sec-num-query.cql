library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define SocialSecurityNumber:
  First(Patient.identifier.where(system = 'http://hl7.org/fhir/sid/us-ssn').value)

define IntegerParts:
    from (Split(SocialSecurityNumber, '-')) S
    where ConvertsToInteger(S)

define InInitialPopulation:
    Count(IntegerParts) = 3
