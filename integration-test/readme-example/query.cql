using FHIR version '4.0.0'

context Patient
context Unspecified

define NumberOfPatients:
  Count([Patient])

define AllPatients:
  [Patient]
