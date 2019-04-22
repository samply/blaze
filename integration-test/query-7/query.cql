library Retrieve version '1.0'
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

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

define Qualifies:
  (Exists([Condition: "Morbus Crohn"]) or Exists([Condition: "Colitis ulcerosa"])) and
  Exists([Specimen: "Tumorgewebe"]) and
  Exists([Specimen: "Gesundes Gewebe"]) and
  (Exists([Specimen: "Serum"]) or Exists([Specimen: "Vollblut-EDTA"]))

context Population

define NumberOfPatients:
  Count(Qualifies Q where Q return all Q)
