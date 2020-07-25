library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Specimen

define Patient:
  singleton from ([Patient])

define InInitialPopulation:
  exists([Patient -> Observation: Code '39156-5' from loinc])
