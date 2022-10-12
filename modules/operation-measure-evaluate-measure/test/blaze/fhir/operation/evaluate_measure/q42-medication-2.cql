library "q42-medication-2"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem atc: 'http://fhir.de/CodeSystem/dimdi/atc'

context Unfiltered

define "Temozolomid Refs":
  [Medication: Code 'L01AX03' from atc] M return 'Medication/' + M.id

context Patient

define InInitialPopulation:
  exists from [MedicationStatement] M
    where M.medication.reference in "Temozolomid Refs"
