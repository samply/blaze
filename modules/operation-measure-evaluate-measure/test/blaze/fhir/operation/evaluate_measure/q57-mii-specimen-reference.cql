library "q57-mii-specimen-reference"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem icd10: 'http://fhir.de/CodeSystem/bfarm/icd-10-gm'
codesystem snomed: 'http://snomed.info/sct'

context Patient

define "Criterion 1":
  Patient.gender = 'female'

define "Diagnose E13.9":
  [Condition: Code 'E13.9' from icd10] union
  [Condition: Code 'E13.91' from icd10] union
  [Condition: Code 'E13.90' from icd10]

define "Criterion 2":
  exists (from [Specimen: Code '119364003' from snomed] S
    with "Diagnose E13.9" C
      such that S.extension.where(url='https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Diagnose').first().value.as(Reference).reference = 'Condition/' + C.id) or
  exists (from [Specimen: Code '258590006' from snomed] S
    with "Diagnose E13.9" C
      such that S.extension.where(url='https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Diagnose').first().value.as(Reference).reference = 'Condition/' + C.id) or
  exists (from [Specimen: Code '866034009' from snomed] S
    with "Diagnose E13.9" C
      such that S.extension.where(url='https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Diagnose').first().value.as(Reference).reference = 'Condition/' + C.id) or
  exists (from [Specimen: Code '866035005' from snomed] S
    with "Diagnose E13.9" C
      such that S.extension.where(url='https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Diagnose').first().value.as(Reference).reference = 'Condition/' + C.id) or
  exists (from [Specimen: Code '442427000' from snomed] S
    with "Diagnose E13.9" C
      such that S.extension.where(url='https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Diagnose').first().value.as(Reference).reference = 'Condition/' + C.id) or
  exists (from [Specimen: Code '737089009' from snomed] S
    with "Diagnose E13.9" C
      such that S.extension.where(url='https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Diagnose').first().value.as(Reference).reference = 'Condition/' + C.id)

define InInitialPopulation:
  "Criterion 1" and
  "Criterion 2"
