{
  duration: .extension[] | select(.url == "https://blaze-server.org/fhir/StructureDefinition/eval-duration") | .valueQuantity.value,
  result: .group[0].population[0].count
}
