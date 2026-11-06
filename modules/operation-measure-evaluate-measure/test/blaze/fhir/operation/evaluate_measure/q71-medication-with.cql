library "q71-medication-with"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem atc: 'http://fhir.de/CodeSystem/dimdi/atc'

context Patient

define InInitialPopulation:
  exists from [MedicationStatement] MS
    with [Medication: Code 'L01AX03' from atc] M
      such that MS.medication.reference = 'Medication/' + M.id
