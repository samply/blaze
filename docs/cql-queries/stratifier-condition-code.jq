["system", "code", "display", "count"],
(.group[0].stratifier[0].stratum[]
  | [.value.coding[0].system, .value.coding[0].code, .value.coding[0].display, .population[0].count])
| @csv
