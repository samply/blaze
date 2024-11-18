library "observation-29463-7-value"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
  [Observation: Code '29463-7' from loinc]

define function Value(observation Observation):
  observation.value.value
