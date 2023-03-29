#!/bin/bash -e

BASE="http://localhost:8080/fhir"

for CODE in "17861-6" "39156-5" "29463-7"
do
  echo "Counting Patients with Observations with code $CODE..."
  for i in {1..6}
  do
    blazectl evaluate-measure "docs/performance/cql/observation-$CODE.yml" --server "$BASE" 2> /dev/null |\
      jq -rf docs/performance/cql/duration.jq >> "$CODE.times"
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "$CODE.times" | awk '{t+=$1;n++} END {printf("Avg time: %.3f\n", t/n)}'
done
