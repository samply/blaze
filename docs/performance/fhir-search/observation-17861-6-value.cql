library "observation-17861-6-value"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
  [Observation: Code '17861-6' from loinc]

define function Value(observation Observation):
  observation.value.value
