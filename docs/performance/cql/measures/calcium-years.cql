library "calcium-years"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define Calcium:
  [Observation: Code '49765-1' from loinc]

define function ObsYear(obs Observation):
  year from obs.effective
