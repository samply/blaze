library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'
code body_weight: '29463-7' from loinc
code body_height: '8302-2' from loinc

context Patient

define InInitialPopulation:
    true

define BodyWeight:
  First(from [Observation: body_weight] O return (O.value as Quantity))

define BodyHeight:
  First(from [Observation: body_height] O return convert (O.value as Quantity) to 'm')

define Bmi:
  Round((BodyWeight / (BodyHeight * BodyHeight)).value, 1)
