library "quality-report-observation"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'

context Patient

define "InInitialPopulation":
    [Observation]

// Helper Functions
define function ToCodeableConcept(codings List<Coding>):
  if exists codings then
    CodeableConcept {
      coding: codings
    }
  else
    null

define function NonMatchingCS(concept CodeableConcept, codesystem System.CodeSystem):
  if FHIRHelpers.ToConcept(concept) in codesystem then
    null
  else
    concept

define function "Laborparameter LOINC observed"(Observation Observation):
  ToCodeableConcept(Observation.code.coding.where(system = 'http://loinc.org'))

define function "Inadmissible categorical values Laborparameter LOINC"(Observation Observation):
  NonMatchingCS("Laborparameter LOINC observed"(Observation), loinc)
