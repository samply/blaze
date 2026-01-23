library "q66-icd10-value-set"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

valueset icd10_e10: 'http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/icd-10-gm)concept<<E10'

context Patient

define InInitialPopulation:
  exists [Condition: icd10_e10]
