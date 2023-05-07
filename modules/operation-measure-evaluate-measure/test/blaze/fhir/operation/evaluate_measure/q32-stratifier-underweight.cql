library "q32-stratifier-underweight"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'
code bmi: '39156-5' from loinc

context Patient

define InInitialPopulation:
  true

define Underweight:
  exists from [Observation: bmi] O
    where AgeInYearsAt(O.effective) >= 18
      and O.value as Quantity < 18.6 'kg/m2'
