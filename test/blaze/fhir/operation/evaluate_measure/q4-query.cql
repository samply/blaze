library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem icd10: 'http://hl7.org/fhir/sid/icd-10'

define InInitialPopulation:
  exists([Condition: Code 'Z77.8' from icd10])
