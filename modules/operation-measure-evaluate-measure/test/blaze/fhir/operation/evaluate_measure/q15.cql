library q15
using FHIR version '4.0.0'

context Patient

define InInitialPopulation:
  AgeInYears() between 18 and 45
