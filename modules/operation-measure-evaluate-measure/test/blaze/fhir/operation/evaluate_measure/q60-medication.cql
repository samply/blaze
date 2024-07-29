library "q60-medication"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem atc: 'http://fhir.de/CodeSystem/bfarm/atc'

context Unfiltered

define AcarboseA10BF01Ref:
  from [Medication: Code 'A10BF01' from atc] M
    return 'Medication/' + M.id

define AcetohexamidA10BB31Ref:
  from [Medication: Code 'A10BB31' from atc] M
    return 'Medication/' + M.id

define AlbiglutidA10BJ04Ref:
  from [Medication: Code 'A10BJ04' from atc] M
    return 'Medication/' + M.id

context Patient

define InInitialPopulation:
  exists (from [MedicationAdministration] M
    where M.medication.reference in AcarboseA10BF01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AcetohexamidA10BB31Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AlbiglutidA10BJ04Ref)
