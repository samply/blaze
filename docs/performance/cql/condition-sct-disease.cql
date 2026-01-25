library "condition-sct-disease"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

valueset sct_disease: 'http://fhir.org/VCL?v1=(http://snomed.info/sct)concept<<22253000'

context Patient

define InInitialPopulation:
  exists [Condition: sct_disease]
