library "q55-stratifier-bmi-observation"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
    [Observation: Code '39156-5' from loinc]

define function Bmi(observation Observation):
  observation.value
