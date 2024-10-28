#!/bin/bash -e

# issues an async request and inspects the resulting job

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

if [[ "$OSTYPE" == "darwin"* ]]; then
  DATE_CMD="gdate"
else
  DATE_CMD="date"
fi

BASE="http://localhost:8080/fhir"
HEADERS=$(curl -s -H 'Prefer: respond-async' -H 'Accept: application/fhir+json' -o /dev/null -D - "$BASE/Observation?code=http://loinc.org|8310-5&_summary=count")
JOB_ID=$(echo "$HEADERS" | grep -i content-location | tr -d '\r' | cut -d '/' -f6)

# wait to fetch the completed job
sleep 1
JOB=$(curl -s -H 'Accept: application/fhir+json' "$BASE/__admin/Task/$JOB_ID")

test "profile URL" "$(echo "$JOB" | jq -r '.meta.profile[]')" "https://samply.github.io/blaze/fhir/StructureDefinition/AsyncInteractionJob"
test "status" "$(echo "$JOB" | jq -r '.status')" "completed"

AUTHORED_ON_ISO=$(echo "$JOB" | jq -r '.authoredOn')
AUTHORED_ON_EPOCH_SECONDS=$($DATE_CMD -d "$AUTHORED_ON_ISO" +%s)
NOW_EPOCH_SECONDS=$($DATE_CMD +%s)
if ((NOW_EPOCH_SECONDS - AUTHORED_ON_EPOCH_SECONDS < 10)); then
  echo "✅ the authoredOn dateTime is set and current"
else
  echo "🆘 the authoredOn dateTime is $AUTHORED_ON_ISO, but should be a current dateTime"
  exit 1
fi

PARAMETER_URI="https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobParameter"

input_expr() {
  echo ".input[] | select(.type.coding[] | select(.system == \"$PARAMETER_URI\" and .code == \"$1\"))"
}

test "t" "$(echo "$JOB" | jq -r "$(input_expr "t") | .valueUnsignedInt")" "0"

OUTPUT_URI="https://samply.github.io/blaze/fhir/CodeSystem/AsyncInteractionJobOutput"

output_expr() {
  echo ".output[] | select(.type.coding[] | select(.system == \"$OUTPUT_URI\" and .code == \"$1\"))"
}

PROCESSING_DURATION="$(echo "$JOB" | jq "$(output_expr "processing-duration") | .valueQuantity")"
test "processing-duration unit system" "$(echo "$PROCESSING_DURATION" | jq -r .system)" "http://unitsofmeasure.org"
test "processing-duration unit code" "$(echo "$PROCESSING_DURATION" | jq -r .code)" "s"

# History
JOB_HISTORY=$(curl -s -H 'Accept: application/fhir+json' "$BASE/__admin/Task/$JOB_ID/_history")

test "history resource type" "$(echo "$JOB_HISTORY" | jq -r '.resourceType')" "Bundle"
test "history bundle type" "$(echo "$JOB_HISTORY" | jq -r '.type')" "history"
test "history total" "$(echo "$JOB_HISTORY" | jq -r '.total')" "3"
test "history 0 status" "$(echo "$JOB_HISTORY" | jq -r '.entry[0].resource.status')" "completed"
test "history 1 status" "$(echo "$JOB_HISTORY" | jq -r '.entry[1].resource.status')" "in-progress"
test "history 2 status" "$(echo "$JOB_HISTORY" | jq -r '.entry[2].resource.status')" "ready"
