using FHIR version '4.0.0'

context Patient
context Unfiltered

define NumberOfPatients:
  Count([Patient])

define AllPatients:
  [Patient]
