#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
START_EPOCH="$(date +"%s")"
PATIENT_TOTAL="$(curl -sH 'Accept: application/fhir+json' "$BASE/Patient?_summary=count" | jq -r .total)"

for CODE in "body-weight"
do
  for VALUE in "10" "50" "100"
  do
    echo "Counting Patients with Observations with code $CODE and value $VALUE..."
    COUNT="$(blazectl --server "$BASE" evaluate-measure "$SCRIPT_DIR/observation-$CODE-$VALUE.yml" 2> /dev/null | jq -r '.group[0].population[0].count')"
    for i in {0..21}
    do
      blazectl evaluate-measure "cql/observation-$CODE-$VALUE.yml" --server "$BASE" 2> /dev/null |\
        jq -rf cql/duration.jq >> "$START_EPOCH-$CODE-$VALUE.times"
    done

    calc-cql-print-stats "$START_EPOCH-$CODE-$VALUE.times" "$PATIENT_TOTAL" "$COUNT"
  done
done
