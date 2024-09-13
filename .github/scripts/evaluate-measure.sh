#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"
. "$SCRIPT_DIR/evaluate-measure-util.sh"

evaluate_measure() {
  curl -s "$1/Measure/\$evaluate-measure?measure=urn:uuid:$2&periodStart=2000&periodEnd=2030"
}

BASE="http://localhost:8080/fhir"
NAME="$1"
EXPECTED_COUNT="$2"

MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$MEASURE_URI" "$NAME" | transact "$BASE" > /dev/null

REPORT=$(evaluate_measure "$BASE" "$MEASURE_URI")
COUNT=$(echo "$REPORT" | jq -r ".group[0].population[0].count")

if [ "$COUNT" = "$EXPECTED_COUNT" ]; then
  echo "✅ count ($COUNT) equals the expected count"
else
  echo "🆘 count ($COUNT) != $EXPECTED_COUNT"
  echo "Report:"
  echo "$REPORT" | jq .
  exit 1
fi
