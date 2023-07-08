#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
START_EPOCH="$(date +"%s")"
PATIENT_TOTAL="$(curl -sH 'Accept: application/fhir+json' "$BASE/Patient?_summary=count" | jq -r .total)"
CODE="$1"

echo "Counting Patients with Observations with code $CODE..."
COUNT="$(blazectl --server "$BASE" evaluate-measure "$SCRIPT_DIR/observation-$CODE.yml" 2> /dev/null | jq -r '.group[0].population[0].count')"
for i in {0..6}
do
  blazectl --server "$BASE" evaluate-measure "$SCRIPT_DIR/observation-$CODE.yml" 2> /dev/null |\
    jq -rf "$SCRIPT_DIR/duration.jq" >> "$START_EPOCH-$CODE.times"
done

calc-cql-print-stats "$START_EPOCH-$CODE.times" "$PATIENT_TOTAL" "$COUNT"
