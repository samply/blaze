#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
START_EPOCH="$(date +"%s")"
PATIENT_TOTAL="$(curl -sH 'Accept: application/fhir+json' "$BASE/Patient?_summary=count" | jq -r .total)"
FILE="$1"
OUTPUT_HEADER="${2:-true}"

if [ "true" = "$OUTPUT_HEADER" ]; then
  echo "Counting Patients with criteria from $FILE..."
fi
REPORT="$(blazectl --server "$BASE" evaluate-measure --force-sync "$SCRIPT_DIR/$FILE.yml" 2> /dev/null)"

if [ "true" = "$OUTPUT_HEADER" ]; then
  echo "Bloom filter ratio: $(echo "$REPORT" | jq -rf "$SCRIPT_DIR/bloom-filter-ratio.jq")"
fi

COUNT="$(echo "$REPORT" | jq -r '.group[0].population[0].count')"

sleep 10
for i in {0..3}
do
  sleep 1
  blazectl --server "$BASE" evaluate-measure --force-sync "$SCRIPT_DIR/$FILE.yml" 2> /dev/null |\
    jq -rf "$SCRIPT_DIR/duration.jq" >> "$START_EPOCH-$FILE.times"
done

calc-cql-print-stats "$START_EPOCH-$FILE.times" "$PATIENT_TOTAL" "$COUNT"
