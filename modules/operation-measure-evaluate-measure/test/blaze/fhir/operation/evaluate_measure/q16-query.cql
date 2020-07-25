library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem FastingStatus: 'http://terminology.hl7.org/CodeSystem/v2-0916'

context Specimen

define InInitialPopulation:
  Specimen.collection.fastingStatus.coding contains Code 'NF' from FastingStatus
