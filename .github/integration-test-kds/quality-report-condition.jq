["system", "code", "display", "text", "count"],
(.group[0].stratifier[].stratum[]
  | [.value.coding[0].system, .value.coding[0].code, .value.coding[0].display, .value.text, .population[0].count])
| @csv
