library "observation-category-laboratory"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem "observation-category": 'http://terminology.hl7.org/CodeSystem/observation-category'
code "laboratory": 'laboratory' from "observation-category"

context Patient

define InInitialPopulation:
  [Observation: category in "laboratory"]
