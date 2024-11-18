library "q56-stratifier-observation-code-value"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
    [Observation]

define function Code(observation Observation):
  observation.code

define function Value(observation Observation):
  observation.value
