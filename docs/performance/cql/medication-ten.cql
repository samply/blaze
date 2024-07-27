library "medication-two"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem rxnorm: 'http://www.nlm.nih.gov/research/umls/rxnorm'

context Unfiltered

define "Medication 1803932 Refs":
  [Medication: Code '1803932' from rxnorm] M return 'Medication/' + M.id

define "Medication 1736854 Refs":
  [Medication: Code '1736854' from rxnorm] M return 'Medication/' + M.id

define "Medication 583214 Refs":
  [Medication: Code '583214' from rxnorm] M return 'Medication/' + M.id

define "Medication 854252 Refs":
  [Medication: Code '854252' from rxnorm] M return 'Medication/' + M.id

define "Medication 1804799 Refs":
  [Medication: Code '1804799' from rxnorm] M return 'Medication/' + M.id

define "Medication 309362 Refs":
  [Medication: Code '309362' from rxnorm] M return 'Medication/' + M.id

define "Medication 1000126 Refs":
  [Medication: Code '1000126' from rxnorm] M return 'Medication/' + M.id

define "Medication 2123111 Refs":
  [Medication: Code '2123111' from rxnorm] M return 'Medication/' + M.id

define "Medication 854235 Refs":
  [Medication: Code '854235' from rxnorm] M return 'Medication/' + M.id

define "Medication 198440 Refs":
  [Medication: Code '198440' from rxnorm] M return 'Medication/' + M.id

context Patient

define InInitialPopulation:
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 1803932 Refs") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 1736854 Refs") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 583214 Refs") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 854252 Refs") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 1804799 Refs") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 309362 Refs") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 1000126 Refs") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 2123111 Refs") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 854235 Refs") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Medication 198440 Refs")
