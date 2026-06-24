((first(.extension[]
  | select(.url == "https://blaze-server.org/fhir/StructureDefinition/bloom-filter-ratio")
  ).valueRatio?.numerator?.value // 0) | tostring) + "/" +
((first(.extension[]
  | select(.url == "https://blaze-server.org/fhir/StructureDefinition/bloom-filter-ratio")
  ).valueRatio?.denominator?.value // 0) | tostring)
