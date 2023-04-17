#!/bin/bash -e

BASE="http://localhost:8080/fhir"
START_EPOCH="$(date +"%s")"

for CODE in "laboratory"
do
  echo "Counting Observations with code $CODE..."
  for i in {0..50}
  do
    blazectl evaluate-measure "cql/observation-category-$CODE.yml" --server "$BASE" 2> /dev/null |\
      jq -rf cql/duration.jq >> "$START_EPOCH-$CODE.times"
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "$START_EPOCH-$CODE.times" |\
    awk '{sum += $1; sumsq += $1^2} END {printf("Avg    : %.3f\nStdDev : %.3f\n", sum/NR, sqrt(sumsq/NR - (sum/NR)^2))}'
done
