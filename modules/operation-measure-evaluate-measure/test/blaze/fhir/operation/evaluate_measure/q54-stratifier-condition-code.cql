library "q54-stratifier-condition-code"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  [Condition]

define function Code(condition Condition):
  condition.code
