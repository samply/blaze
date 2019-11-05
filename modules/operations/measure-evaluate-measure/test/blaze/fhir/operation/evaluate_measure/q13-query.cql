library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem icd10: 'http://hl7.org/fhir/sid/icd-10'

define InInitialPopulation:
  exists([Condition: Code 'Z78.0' from icd10]) or
  exists([Condition: Code 'Z78.1' from icd10]) or
  exists([Condition: Code 'Z78.2' from icd10]) or
  exists([Condition: Code 'Z78.3' from icd10]) or
  exists([Condition: Code 'Z78.4' from icd10]) or
  exists([Condition: Code 'Z78.5' from icd10]) or
  exists([Condition: Code 'Z78.6' from icd10]) or
  exists([Condition: Code 'Z78.7' from icd10]) or
  exists([Condition: Code 'Z78.8' from icd10]) or
  exists([Condition: Code 'Z78.9' from icd10])
