library Retrieve version '1.0'
using FHIR version '3.0.0'
include FHIRHelpers version '3.0.0'

codesystem "ICD-10-GM:2019": 'http://fhir.de/CodeSystem/dimdi/icd-10-gm' version '2019'
code "Seronegative chronische Polyarthritis": 'M06.9' from "ICD-10-GM:2019"

context Population

define Patients:
  from [Patient] P
    with [Condition: "Seronegative chronische Polyarthritis"] C
      such that P = C.subject and CalculateAgeInYearsAt(P.birthDate, ToDate(C.onset as dateTime)) >= 18

define NumberOfPatients:
  Count(Patients)
