library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

parameter "Measurement Period" Interval<DateTime> default Interval[@2020-01-01T, @2020-12-31T]

define InInitialPopulation:
  exists
    from [Observation: Code {system: 'http://loinc.org', code: '39156-5'}] O
    where O.effective as dateTime in "Measurement Period"
