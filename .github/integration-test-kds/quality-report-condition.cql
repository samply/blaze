library "kds-quality-report-condition"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

valueset "Klinischer Status": 'http://hl7.org/fhir/ValueSet/condition-clinical'
valueset "Verifizierungsstatus": 'http://hl7.org/fhir/ValueSet/condition-ver-status'
valueset Diagnosesicherheit: 'https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT'
valueset Seitenlokalisation: 'https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_SEITENLOKALISATION'
valueset "Mehrfachkodierungs-Kennzeichen": 'http://fhir.de/ValueSet/icd-10-gm-mehrfachcodierungs-kennzeichen'
valueset "Körperstelle": 'http://hl7.org/fhir/ValueSet/body-site'

context Patient

define InInitialPopulation:
  [Condition]

define function ToCodeableConcept(codings List<Coding>):
  if exists codings then
    CodeableConcept {
      coding: codings
    }
  else
    null

define function NonMatching(concept CodeableConcept, valueset System.ValueSet):
  if FHIRHelpers.ToConcept(concept) in valueset then
    null
  else
    concept

// Feststellungsdatum
define function "Feststellungsdatum missing"(condition Condition):
  not exists condition.extension.where(url = 'http://hl7.org/fhir/StructureDefinition/condition-assertedDate').value

// ReferenzPrimaerdiagnose
define function "ReferenzPrimaerdiagnose missing"(condition Condition):
  not exists condition.extension.where(url = 'http://hl7.org/fhir/StructureDefinition/condition-related').value

// Klinischer Status
define function "Klinischer Status observed"(condition Condition):
  condition.clinicalStatus

define function "Klinischer Status non-matching"(condition Condition):
  NonMatching(condition.clinicalStatus, "Klinischer Status")

// Verifizierungsstatus
define function "Verifizierungsstatus observed"(condition Condition):
  condition.verificationStatus

define function "Verifizierungsstatus non-matching"(condition Condition):
  NonMatching(condition.verificationStatus, "Verifizierungsstatus")

// Diagnose
define function "Diagnose observed"(condition Condition):
  condition.code

// Diagnosesicherheit
define function "Diagnosesicherheit observed"(condition Condition):
  ToCodeableConcept(condition.code.coding.extension.where(url = 'http://fhir.de/StructureDefinition/icd-10-gm-diagnosesicherheit').value)

define function "Diagnosesicherheit non-matching"(condition Condition):
  NonMatching("Diagnosesicherheit observed"(condition), Diagnosesicherheit)

// Seitenlokalisation
define function "Seitenlokalisation observed"(condition Condition):
  ToCodeableConcept(condition.code.coding.extension.where(url = 'http://fhir.de/StructureDefinition/seitenlokalisation').value)

define function "Seitenlokalisation non-matching"(condition Condition):
  NonMatching("Seitenlokalisation observed"(condition), Seitenlokalisation)

// Mehrfachkodierungs-Kennzeichen
define function "Mehrfachkodierungs-Kennzeichen observed"(condition Condition):
  ToCodeableConcept(condition.code.coding.extension.where(url = 'http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen').value)

define function "Mehrfachkodierungs-Kennzeichen non-matching"(condition Condition):
  NonMatching("Mehrfachkodierungs-Kennzeichen observed"(condition), "Mehrfachkodierungs-Kennzeichen")

// Körperstelle
define function "Körperstelle observed"(condition Condition):
  condition.bodySite.first()

define function "Körperstelle non-matching"(condition Condition):
  NonMatching(condition.bodySite.first(), "Körperstelle")

// Patient
define function "Patient missing"(condition Condition):
  condition.subject.reference is null

// Encounter
define function "Encounter missing"(condition Condition):
  condition.encounter.reference is null

// Klinischer Zeitraum
define function "Klinischer Zeitraum missing"(condition Condition):
  condition.onset is null

define function "Klinischer Zeitraum type"(condition Condition):
  case
    when condition.onset is null then 'null'
    when condition.onset is dateTime then 'dateTime'
    when condition.onset is Period then 'Period'
    else 'unknown'
  end

define function "Klinischer Zeitraum Ende ist vor klinischer Zeitraum Start"(condition Condition):
  condition.onset.start > condition.onset.end

// Dokumentationsdatum
define function "Dokumentationsdatum missing"(condition Condition):
  condition.recordedDate is null

// Hinweis
define function "Anzahl Hinweise"(condition Condition):
  Length(condition.note)
