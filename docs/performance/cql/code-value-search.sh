#!/bin/bash -e

BASE="http://localhost:8080/fhir"

for CODE in "body-weight"
do
  for VALUE in "10" "50" "100"
  do
    echo "Counting Patients with Observations with code $CODE and value $VALUE..."
    for i in {1..6}
    do
      blazectl evaluate-measure "docs/performance/cql/observation-$CODE-$VALUE.yml" --server "$BASE" 2> /dev/null |\
        jq -rf docs/performance/cql/duration.jq >> "$CODE-$VALUE.times"
    done

    # Skip first line because it will not benefit from caching
    tail -n +2 "$CODE-$VALUE.times" | awk '{t+=$1;n++} END {printf("Avg time: %.2f\n", t/n)}'
  done
done
