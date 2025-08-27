library "q28-relationship-procedure-condition"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem icd10: 'http://hl7.org/fhir/sid/icd-10'
codesystem idco3: 'urn:oid:2.16.840.1.113883.6.43.1'
codesystem therapie_art: 'http://dktk.dkfz.de/fhir/onco/core/CodeSystem/SYSTTherapieartCS'

define Strahlentherapien:
  [Procedure: category ~ Code 'ST' from therapie_art]

define EwingSarkomMitStrahlentherapie:
  exists
    from [Condition: Code 'C41.9' from icd10] C
      with Strahlentherapien P
        such that P.reasonReference.reference = 'Condition/' + C.id
    where exists from C.concept.bodySite BS where BS as CodeableConcept ~ Code 'C44.6' from idco3

define InInitialPopulation:
  EwingSarkomMitStrahlentherapie
