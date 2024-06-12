{
  duration: .extension[] | select(.url == "https://samply.github.io/blaze/fhir/StructureDefinition/eval-duration") | .valueQuantity.value,
  result: .group[0].population[0].count
}
