library "inpatient-condition-code"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem act: 'http://terminology.hl7.org/CodeSystem/v3-ActCode'

context Patient

define "Inpatient Encounter":
  [Encounter: class in Code 'IMP' from act]

define InInitialPopulation:
  from [Condition] C
    with "Inpatient Encounter" E
    such that C.encounter.reference = 'Encounter/' + E.id

define function ConditionCode(condition Condition):
  condition.code
