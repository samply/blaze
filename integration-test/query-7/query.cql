library Retrieve version '1.0'
using FHIR version '3.0.0'
include FHIRHelpers version '3.0.0'

codesystem "ICD-10-GM:2019": 'http://fhir.de/CodeSystem/dimdi/icd-10-gm' version '2019'
codesystem "Sample Context": 'urn:mdr16:dataelement:20:1'
codesystem "Sample type liquid": 'urn:mdr16:dataelement:16:1'
code "Morbus Crohn": 'K50.9' from "ICD-10-GM:2019"
code "Colitis ulcerosa": 'K51.9' from "ICD-10-GM:2019"
code "Tumorgewebe": 'tumor_tissue' from "Sample Context"
code "Gesundes Gewebe": 'healthy_tissue' from "Sample Context"
code "Serum": 'serum' from "Sample type liquid"
code "Vollblut-EDTA": 'whole_blood-EDTA' from "Sample type liquid"

context Patient
context Population

define Patients:
  from [Patient] P
    with ([Condition: "Morbus Crohn"] union [Condition: "Colitis ulcerosa"]) C
      such that P = C.subject
    with [Specimen: "Tumorgewebe"] S
      such that P = S.subject
    with [Specimen: "Gesundes Gewebe"] S
      such that P = S.subject
    with ([Specimen: "Serum"] union [Specimen: "Vollblut-EDTA"]) S
      such that P = S.subject

define NumberOfPatients:
  Count(Patients)
