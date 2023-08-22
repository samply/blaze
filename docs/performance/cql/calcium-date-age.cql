library "calcium-date-age"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define InInitialPopulation:
  exists [Observation: Code '49765-1' from loinc] O
  where year from O.effective between 2015 and 2019
  and AgeInYearsAt(O.effective) < 59
