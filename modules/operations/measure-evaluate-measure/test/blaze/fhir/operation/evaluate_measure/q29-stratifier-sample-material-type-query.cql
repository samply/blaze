library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem SampleMaterialType: 'https://fhir.bbmri.de/CodeSystem/SampleMaterialType'

context Specimen

define InInitialPopulation:
  true

define TypeCodes:
  from Specimen.type.coding C return FHIRHelpers.ToCode(C)

define SampleMaterialTypeCategory:
  case
    when
      exists (TypeCodes intersect {
        Code 'whole-blood' from SampleMaterialType,
        Code 'bone-marrow' from SampleMaterialType,
        Code 'buffy-coat' from SampleMaterialType,
        Code 'peripheral-blood-cells-vital' from SampleMaterialType,
        Code 'blood-plasma' from SampleMaterialType,
        Code 'plasma-edta' from SampleMaterialType,
        Code 'plasma-citrat' from SampleMaterialType,
        Code 'plasma-heparin' from SampleMaterialType,
        Code 'plasma-cell-free' from SampleMaterialType,
        Code 'plasma-other' from SampleMaterialType,
        Code 'blood-serum' from SampleMaterialType,
        Code 'ascites' from SampleMaterialType,
        Code 'csf-liquor' from SampleMaterialType,
        Code 'urine' from SampleMaterialType,
        Code 'liquid-other' from SampleMaterialType
      })
    then 'liquid'
    when
      exists (TypeCodes intersect {
        Code 'tissue-ffpe' from SampleMaterialType,
        Code 'tumor-tissue-ffpe' from SampleMaterialType,
        Code 'normal-tissue-ffpe' from SampleMaterialType,
        Code 'other-tissue-ffpe' from SampleMaterialType,
        Code 'tissue-frozen' from SampleMaterialType,
        Code 'tumor-tissue-frozen' from SampleMaterialType,
        Code 'normal-tissue-frozen' from SampleMaterialType,
        Code 'other-tissue-frozen' from SampleMaterialType,
        Code 'tissue-other' from SampleMaterialType
      })
    then 'tissue'
    else 'other'
  end
