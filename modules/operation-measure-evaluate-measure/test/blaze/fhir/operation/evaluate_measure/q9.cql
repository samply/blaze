library q9
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem StorageTemperature: 'https://fhir.bbmri.de/CodeSystem/StorageTemperature'

context Specimen

define InInitialPopulation:
  exists
    from Specimen.extension E
    where E.url = 'https://fhir.bbmri.de/StructureDefinition/StorageTemperature'
      and E.value as CodeableConcept ~ Code 'temperatureRoom' from StorageTemperature
