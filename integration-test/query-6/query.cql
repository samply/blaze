library Retrieve version '1.0'
using FHIR version '3.0.0'
include FHIRHelpers version '3.0.0'

codesystem "ICD-10-GM:2019": 'http://fhir.de/CodeSystem/dimdi/icd-10-gm' version '2019'
codesystem "Sample type tissue": 'urn:mdr16:dataelement:15:1'
code "Brustkrebs": 'C50' from "ICD-10-GM:2019"
code "tissue frozen": 'tissue frozen' from "Sample type tissue"

context Patient
context Population

define Patients:
  from [Patient] P
    with [Condition: "Brustkrebs"] C
      such that P = C.subject
    with [Specimen: "tissue frozen"] C
      such that P = C.subject

define NumberOfPatients:
  Count(Patients)
