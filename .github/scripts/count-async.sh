#!/bin/bash -e

# Takes a LOINC `code` and `count` and issues am async _summary=count request.
# Tests that the total value in result bundle equals to `count`.

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

START_EPOCH="$(date +"%s")"

eclipsed() {
  EPOCH="$(date +"%s")"
  echo $((EPOCH - START_EPOCH))
}

BASE="http://localhost:8080/fhir"
CODE="$1"
COUNT="$2"
HEADERS=$(curl -s -H 'Prefer: respond-async' -H 'Accept: application/fhir+json' -o /dev/null -D - "$BASE/Observation?code=http://loinc.org|$CODE&_summary=count")
STATUS_URL=$(echo "$HEADERS" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$STATUS_URL")" != "200") ]]; do
  sleep 1
done

RESPONSE_BUNDLE="$(curl -s -H 'Accept: application/fhir+json' "$STATUS_URL")"

test "response bundle type" "$(echo "$RESPONSE_BUNDLE" | jq -r .type)" "batch-response"

RESULT_BUNDLE="$(echo "$RESPONSE_BUNDLE" | jq '.entry[0].resource')"

test "result bundle type" "$(echo "$RESULT_BUNDLE" | jq -r .type)" "searchset"
test "result total" "$(echo "$RESULT_BUNDLE" | jq -r .total)" "$COUNT"
