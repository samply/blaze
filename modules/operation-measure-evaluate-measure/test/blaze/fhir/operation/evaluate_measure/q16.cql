library q16
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem FastingStatus: 'http://terminology.hl7.org/CodeSystem/v2-0916'

context Specimen

define InInitialPopulation:
  Specimen.collection.fastingStatus as CodeableConcept ~ Code 'NF' from FastingStatus
