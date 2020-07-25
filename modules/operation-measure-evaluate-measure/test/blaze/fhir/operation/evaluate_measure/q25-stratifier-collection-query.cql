library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Specimen

define InInitialPopulation:
    true

define Collection:
  First(from Specimen.extension E
    where E.url = 'https://fhir.bbmri.de/StructureDefinition/Custodian'
    return (E.value as Reference).reference)
