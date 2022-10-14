library "q34-medication"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem atc: 'http://fhir.de/CodeSystem/dimdi/atc'

context Unfiltered

define "Temozolomid Ref":
  'Medication/' + singleton from (
    [Medication: Code 'L01AX03' from atc] M return FHIRHelpers.ToString(M.id))

context Patient

define InInitialPopulation:
  exists from [MedicationStatement] M
    where (M.medication as Reference).reference = "Temozolomid Ref"
