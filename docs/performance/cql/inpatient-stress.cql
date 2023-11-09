library "inpatient-stress"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'
codesystem act: 'http://terminology.hl7.org/CodeSystem/v3-ActCode'

context Patient

define Stress:
  [Condition: Code '73595000' from sct]

define "Inpatient Encounter":
  [Encounter: class in Code 'IMP' from act]

define InInitialPopulation:
  exists Stress C
    with "Inpatient Encounter" E
    such that C.encounter.reference = 'Encounter/' + E.id
