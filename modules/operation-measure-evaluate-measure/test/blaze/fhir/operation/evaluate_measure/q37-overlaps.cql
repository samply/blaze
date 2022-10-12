library "q37-overlaps"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem snomed: 'http://snomed.info/sct'

define InInitialPopulation:
  exists
    from [Procedure: Code '431182000' from snomed] P
    where P.performed in Interval[@2020-02-01T, @2020-06-01T]
       or P.performed overlaps Interval[@2020-02-01T, @2020-06-01T]
