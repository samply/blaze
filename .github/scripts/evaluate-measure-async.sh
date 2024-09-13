#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"
. "$SCRIPT_DIR/evaluate-measure-util.sh"

START_EPOCH="$(date +"%s")"

eclipsed() {
  EPOCH="$(date +"%s")"
  echo $((EPOCH - START_EPOCH))
}

evaluate_measure() {
  curl -s -H "Prefer: respond-async" -o /dev/null -D - "$1/Measure/\$evaluate-measure?measure=urn:uuid:$2&periodStart=2000&periodEnd=2030"
}

BASE="http://localhost:8080/fhir"
NAME="$1"
EXPECTED_COUNT="$2"

MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$MEASURE_URI" "$NAME" | transact "$BASE" > /dev/null

HEADERS=$(evaluate_measure "$BASE" "$MEASURE_URI")
STATUS_URL=$(echo "$HEADERS" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$STATUS_URL")" != "200") ]]; do
  sleep 1
done

BUNDLE="$(curl -s -H 'Accept: application/fhir+json' "$STATUS_URL")"
COUNT=$(echo "$BUNDLE" | jq -r ".entry[0].resource.group[0].population[0].count")

if [ "$COUNT" = "$EXPECTED_COUNT" ]; then
  echo "✅ count ($COUNT) equals the expected count"
else
  echo "🆘 count ($COUNT) != $EXPECTED_COUNT"
  echo "Report:"
  echo "$BUNDLE" | jq .
  exit 1
fi
