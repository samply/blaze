((first(.extension[]
  | select(.url == "https://samply.github.io/blaze/fhir/StructureDefinition/bloom-filter-ratio")
  ).valueRatio?.numerator?.value // 0) | tostring) + "/" +
((first(.extension[]
  | select(.url == "https://samply.github.io/blaze/fhir/StructureDefinition/bloom-filter-ratio")
  ).valueRatio?.denominator?.value // 0) | tostring)
