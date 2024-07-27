library "medication-1"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem rxnorm: 'http://www.nlm.nih.gov/research/umls/rxnorm'

context Unfiltered

define "Medication Refs":
  [Medication: Code '854252' from rxnorm] M return 'Medication/' + M.id

context Patient

define InInitialPopulation:
  exists from [MedicationAdministration] M
    where M.medication.reference in "Medication Refs"
