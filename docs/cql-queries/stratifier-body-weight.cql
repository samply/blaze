library "stratifier-body-weight"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'
code "Body Weight": '29463-7' from loinc

context Patient

define InInitialPopulation:
  [Observation: "Body Weight"]

define function QuantityValue(observation Observation):
  observation.value as Quantity
