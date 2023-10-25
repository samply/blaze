library "q43-medication-3"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem atc: 'http://fhir.de/CodeSystem/dimdi/atc'

context Unfiltered

define "Temozolomid Medications":
  [Medication: Code 'L01AX03' from atc]

context Patient

define InInitialPopulation:
  exists from [MedicationStatement] MS
    with "Temozolomid Medications" M
      such that MS.medication.reference = 'Medication/' + M.id
