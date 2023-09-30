library "condition-ten-frequent"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'

context Patient

define InInitialPopulation:
  exists [Condition: Code '72892002' from sct] or
  exists [Condition: Code '10509002' from sct] or
  exists [Condition: Code '36955009' from sct] or
  exists [Condition: Code '195662009' from sct] or
  exists [Condition: Code '162864005' from sct] or
  exists [Condition: Code '49727002' from sct] or
  exists [Condition: Code '444814009' from sct] or
  exists [Condition: Code '386661006' from sct] or
  exists [Condition: Code '840539006' from sct] or
  exists [Condition: Code '840544004' from sct]
