library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'
code body_weight: '29463-7' from loinc
code body_height: '8302-2' from loinc

context Patient

define InInitialPopulation:
    true

define MostRecentBodyWeight:
  First(
    from [Observation: body_weight] O
    sort by FHIRHelpers.ToDateTime(effective as dateTime) desc)

define MostRecentBodyHeight:
  First(
    from [Observation: body_height] O
    sort by FHIRHelpers.ToDateTime(effective as dateTime) desc)

define MostRecentBodyHeightInMeters:
  convert MostRecentBodyHeight.value to 'm'

define Bmi:
  Round((MostRecentBodyWeight.value / (MostRecentBodyHeightInMeters * MostRecentBodyHeightInMeters)).value, 1)
