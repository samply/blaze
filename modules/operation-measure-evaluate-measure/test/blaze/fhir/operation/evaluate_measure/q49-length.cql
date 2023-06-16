library "q49-length"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

define InInitialPopulation:
  Length([Observation]) > 1
