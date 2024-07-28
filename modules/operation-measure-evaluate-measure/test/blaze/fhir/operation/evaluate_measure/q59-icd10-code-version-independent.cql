library "q59-icd10-code-version-independent"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem icd10: 'http://fhir.de/CodeSystem/bfarm/icd-10-gm'

context Patient

define InInitialPopulation:
  exists [Condition: Code 'E10.6' from icd10]
