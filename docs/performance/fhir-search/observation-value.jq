# This jq script takes a MeasureReport and returns the values in order with the
# sum of the population counts up to that value so far.
#
# The count contains the number of patients which have a value less equal the
# value.

["value", "count"],
( [ [ .group[0].stratifier[0].stratum[] ] | sort_by(.value.text | tonumber) []
  | [( .value.text | tonumber ), .population[0].count]
] as $rows
| reduce range(0; $rows | length) as $i ([]; . + [ [ $rows[$i][0], ($rows[0:$i+1] | map(.[1]) | add) ] ]) | .[])
| @csv
