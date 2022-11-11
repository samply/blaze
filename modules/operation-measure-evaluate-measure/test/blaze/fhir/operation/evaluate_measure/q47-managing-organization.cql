library "q47-managing-organization"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Unfiltered

define function "Organization Ref"(name System.String):
  'Organization/' + singleton from (
    [Organization] O where O.name = name return FHIRHelpers.ToString(O.id))

context Patient

define InInitialPopulation:
  Patient.managingOrganization.reference = "Organization Ref"('UKL Leipzig')
