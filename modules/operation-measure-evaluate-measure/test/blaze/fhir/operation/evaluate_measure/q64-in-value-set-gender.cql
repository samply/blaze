library "q64-in-value-set-gender"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

valueset FemaleAdministrativeSex: 'urn:oid:2.16.840.1.113883.3.560.100.2'

context Patient

define InInitialPopulation:
  Patient.gender in FemaleAdministrativeSex
