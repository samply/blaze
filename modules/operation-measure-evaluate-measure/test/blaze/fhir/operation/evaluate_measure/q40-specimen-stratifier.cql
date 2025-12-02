library "q40-specimen-stratifier"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  [Specimen]

define function SampleType(specimen Specimen):
  specimen.type.coding.where(system = 'https://fhir.bbmri.de/CodeSystem/SampleMaterialType').code.first()
