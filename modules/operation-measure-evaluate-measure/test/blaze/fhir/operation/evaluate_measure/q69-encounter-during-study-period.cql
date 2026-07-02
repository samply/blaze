library "q69-encounter-during-study-period"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

valueset "Outpatient Clinical Encounters": 'urn:oid:2.16.840.1.113883.3.464.1003.101.12.1087'

context Patient

define "Study Period":
  Interval[@2020-01-01T, @2021-01-01T]

define "Has Outpatient Encounter During Study Period":
  exists [Encounter: "Outpatient Clinical Encounters"] E
    where Interval[E.period.start, E.period.end] during "Study Period"

define InInitialPopulation:
  "Has Outpatient Encounter During Study Period"
