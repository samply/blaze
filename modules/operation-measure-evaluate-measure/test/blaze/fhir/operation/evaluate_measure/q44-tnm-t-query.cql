library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'
codesystem tnmt: 'http://dktk.dkfz.de/fhir/onco/core/CodeSystem/TNMTCS'

context Patient

define InInitialPopulation:
  exists
    from [Observation: Code '21908-9' from loinc] O
    where O.component.where(code.coding contains Code '21905-5' from loinc).value.coding contains Code '0' from tnmt
