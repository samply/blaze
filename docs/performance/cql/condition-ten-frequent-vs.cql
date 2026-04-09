library "condition-ten-frequent-vs"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

valueset vs: 'http://fhir.org/VCL?v1=(http://snomed.info/sct)(72892002;10509002;36955009;195662009;162864005;49727002;444814009;386661006;840539006;840544004)'

context Patient

define InInitialPopulation:
  exists [Condition: vs]
