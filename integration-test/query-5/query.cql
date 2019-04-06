library Retrieve version '1.0'
using FHIR version '3.0.0'
include FHIRHelpers version '3.0.0'

codesystem "ICD-10-GM:2019": 'http://fhir.de/CodeSystem/dimdi/icd-10-gm' version '2019'
code "Weichteilsarkom": 'C49' from "ICD-10-GM:2019"
code "Speicheldrüsenkrebs": 'C08' from "ICD-10-GM:2019"
code "Thrombotische Mikroangiopathie": 'M31.1' from "ICD-10-GM:2019"

context Patient
context Population

define "Relevante Diagnosen":
  [Condition: "Weichteilsarkom"] union
  [Condition: "Speicheldrüsenkrebs"] union
  [Condition: "Thrombotische Mikroangiopathie"]

define Patients:
  from [Patient] P
    with "Relevante Diagnosen" C
      such that P = C.subject

define NumberOfPatients:
  Count(Patients)
