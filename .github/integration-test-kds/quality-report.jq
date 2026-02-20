["stratum-index", "system", "code", "display", "text", "count"],
(.group[0].stratifier[]?.stratum | to_entries[]
  | .key as $idx | .value as $s
  | ($s.value.coding[]? // { system: null })
  | [$idx, .system, .code, .display, $s.value.text, $s.population[0].count])
| @csv
