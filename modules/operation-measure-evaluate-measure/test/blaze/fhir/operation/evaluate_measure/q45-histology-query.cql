library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'
codesystem icdo3: 'urn:oid:2.16.840.1.113883.6.43.1'

context Patient

define InInitialPopulation:
  exists
    from [Observation: Code '59847-4' from loinc] O
    where O.value.coding contains Code '8140/3' from icdo3
