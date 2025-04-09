library "q23-stratifier-ageclass-and-gender"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  true

define AgeClass:
  (AgeInYears() div 10) * 10

define Gender:
  if Patient.gender = 'other' then
    case Patient.gender.extension.where(url = 'http://fhir.de/StructureDefinition/gender-amtlich-de').value.code.value
      when 'D' then 'divers'
      when 'X' then 'unbestimmt'
      else 'other'
    end
  else
    Patient.gender
