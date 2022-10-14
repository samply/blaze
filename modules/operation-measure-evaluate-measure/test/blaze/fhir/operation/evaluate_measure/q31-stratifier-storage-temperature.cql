library "q31-stratifier-storage-temperature"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Specimen

define InInitialPopulation:
  true

define StorageTemperatureCodings:
  flatten
    from Specimen.extension E
      where E.url = 'https://fhir.bbmri.de/StructureDefinition/StorageTemperature'
      return E.value.coding

define StorageTemperatureCodes:
  Combine(from StorageTemperatureCodings C
    where C.system = 'https://fhir.bbmri.de/CodeSystem/StorageTemperature'
    return C.code, ', ')
