library "q48-concept"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem icd10: 'http://hl7.org/fhir/sid/icd-10'
code "ICD-10: C61": 'C61' from icd10
codesystem sct: 'http://snomed.info/sct'
code "SNOMED: 254900004": '254900004' from sct
concept prostata: {"ICD-10: C61", "SNOMED: 254900004"}

define InInitialPopulation:
  exists [Condition: prostata]
