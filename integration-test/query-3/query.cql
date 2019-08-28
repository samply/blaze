library Retrieve version '1.0'
using FHIR version '3.0.0'
include FHIRHelpers version '3.0.0'
// TODO: in version 4.0.0 type resolution of Condition.onset is ambiguous:
// TODO: Ambiguous implicit conversion from FHIR.dateTime to System.DateTime or System.DateTime.

codesystem "ICD-10-GM:2019": 'http://fhir.de/CodeSystem/dimdi/icd-10-gm' version '2019'
code "Seronegative chronische Polyarthritis": 'M06.9' from "ICD-10-GM:2019"

context Patient

define Qualifies:
  exists([Condition: "Seronegative chronische Polyarthritis"] C
          where AgeInYearsAt(C.onset) >= 18)

context Unspecified

define NumberOfPatients:
  Count(Qualifies Q where Q return all Q)
