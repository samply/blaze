["year", "count"],
(.group[0].stratifier[0].stratum[] | [.value.text, .population[0].count])
| @csv
