library q6
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem SampleMaterialType: 'https://fhir.bbmri.de/CodeSystem/SampleMaterialType'
codesystem icd_o: 'urn:oid:2.16.840.1.113883.6.43.1'

define InInitialPopulation:
  exists
    from [Specimen: Code 'whole-blood' from SampleMaterialType] S
    where S.collection.bodySite.concept as CodeableConcept ~ Code 'C26.1' from icd_o
