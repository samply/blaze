#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
START_EPOCH="$(date +"%s")"
PATIENT_TOTAL="$(curl -sH 'Accept: application/fhir+json' "$BASE/Patient?_summary=count" | jq -r .total)"
CODE="$1"

echo "Counting Patients with Observations with code $CODE, date between 2015 and 2019 and age of patient at observation date below 18..."

MEASURE_FILE="$SCRIPT_DIR/$CODE-date-age.yml"
TIMES_FILE="$START_EPOCH-$CODE-date-age.times"
COUNT="$(blazectl --server "$BASE" evaluate-measure "$MEASURE_FILE" 2> /dev/null | jq -r '.group[0].population[0].count')"

for i in {0..6}
do
  blazectl --server "$BASE" evaluate-measure "$MEASURE_FILE" 2> /dev/null |\
    jq -rf "$SCRIPT_DIR/duration.jq" >> "$TIMES_FILE"
done
calc-cql-print-stats "$TIMES_FILE" "$PATIENT_TOTAL" "$COUNT"
