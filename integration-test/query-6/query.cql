library Retrieve version '1.0'
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem "ICD-10-GM:2019": 'http://fhir.de/CodeSystem/dimdi/icd-10-gm' version '2019'
codesystem "Sample type tissue": 'urn:mdr16:dataelement:15:1'
code "Brustkrebs": 'C50' from "ICD-10-GM:2019"
code "tissue frozen": 'tissue frozen' from "Sample type tissue"

context Patient

define Qualifies:
  exists([Condition: "Brustkrebs"]) and exists([Specimen: "tissue frozen"])

context Population

define NumberOfPatients:
  Count(Qualifies Q where Q return all Q)
