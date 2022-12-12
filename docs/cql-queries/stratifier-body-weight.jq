["body-weight-value", "body-weight-unit", "count"],
(.group[0].stratifier[0].stratum[]
  | [.extension[0].valueQuantity.value, .extension[0].valueQuantity.code, .population[0].count])
| @csv
