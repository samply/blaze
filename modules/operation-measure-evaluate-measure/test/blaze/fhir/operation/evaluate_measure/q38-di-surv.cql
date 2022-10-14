library "q38-di-surv"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem snomed: 'http://snomed.info/sct'
codesystem icd_10_gm: 'http://fhir.de/CodeSystem/bfarm/icd-10-gm'
codesystem kontaktart: 'http://fhir.de/CodeSystem/kontaktart-de'

context Patient

define "Diabetes mellitus, Typ 1 Diagnose":
  [Condition: Code 'E10.0-' from icd_10_gm] union
  [Condition: Code 'E10.01' from icd_10_gm] union
  [Condition: Code 'E10.1-' from icd_10_gm] union
  [Condition: Code 'E10.11' from icd_10_gm]

define "Stationäre Aufnahme":
  [Encounter: Code 'normalstationaer' from kontaktart]

define InInitialPopulation:
  exists "Stationäre Aufnahme" E
    with "Diabetes mellitus, Typ 1 Diagnose" C
      such that C.encounter.reference = 'Encounter/' + E.id
    where E.period during Interval[@2021-01-01T, @2021-12-31T]
      and AgeInYearsAt(E.period.start) < 5
