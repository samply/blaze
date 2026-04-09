library "condition-ten-rare-vs"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

valueset vs: 'http://fhir.org/VCL?v1=(http://snomed.info/sct)(62718007;234466008;288959006;47505003;698754002;157265008;15802004;14760008;36923009;45816000)'

context Patient

define InInitialPopulation:
  exists [Condition: vs]
