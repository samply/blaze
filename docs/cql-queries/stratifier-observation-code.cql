library "stratifier-observation-code"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  [Observation]

define function Code(observation Observation):
  observation.code
