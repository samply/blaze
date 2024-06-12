library "q50-specimen-condition-reference"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'
codesystem icd: 'http://fhir.de/CodeSystem/bfarm/icd-10-gm'
code "Serum Specimen": '119364003' from sct
code "Diabetes mellitus": 'E13.9' from icd

context Patient

define InInitialPopulation:
    exists [Specimen: "Serum Specimen"] S
      with [Condition: "Diabetes mellitus"] C
        such that (S.extension.where(url='https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Diagnose').first().value as Reference).reference = 'Condition/' + C.id
