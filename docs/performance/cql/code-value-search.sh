#!/bin/bash -e

BASE="http://localhost:8080/fhir"
START_EPOCH="$(date +"%s")"

for CODE in "body-weight"
do
  for VALUE in "10" "50" "100"
  do
    echo "Counting Patients with Observations with code $CODE and value $VALUE..."
    for i in {0..50}
    do
      blazectl evaluate-measure "cql/observation-$CODE-$VALUE.yml" --server "$BASE" 2> /dev/null |\
        jq -rf cql/duration.jq >> "$START_EPOCH-$CODE-$VALUE.times"
    done

    # Skip first line because it will not benefit from caching
    tail -n +2 "$START_EPOCH-$CODE-$VALUE.times" |\
      awk '{sum += $1; sumsq += $1^2} END {printf("Avg    : %.3f\nStdDev : %.3f\n", sum/NR, sqrt(sumsq/NR - (sum/NR)^2))}'
  done
done
