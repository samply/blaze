library "stratifier-observation-laboratory-code"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem category: 'http://terminology.hl7.org/CodeSystem/observation-category'

context Patient

define InInitialPopulation:
  [Observation: category ~ Code 'laboratory' from category]

define function Code(observation Observation):
  observation.code
