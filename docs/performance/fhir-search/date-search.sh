#!/bin/bash -e

BASE="http://localhost:8080/fhir"
START_EPOCH="$(date +"%s")"

for YEAR in "2012" "2019" "2020"
do
  echo "Counting Observations with effective date in $YEAR..."
  echo "The number is : $(curl -s "$BASE/Observation?date=$YEAR&_summary=count" | jq .total)"
  for i in {0..10}
  do
    curl -s "$BASE/Observation?date=$YEAR&_summary=count" -o /dev/null -w '%{time_starttransfer}\n' >> "$START_EPOCH-$YEAR.times"
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "$START_EPOCH-$YEAR.times" |\
    awk '{sum += $1; sumsq += $1^2} END {printf("Avg    : %.3f\nStdDev : %.3f\n", sum/NR, sqrt(sumsq/NR - (sum/NR)^2))}'
done
