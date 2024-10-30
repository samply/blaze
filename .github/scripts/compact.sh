#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

if [[ "$OSTYPE" == "darwin"* ]]; then
  DATE_CMD="gdate"
else
  DATE_CMD="date"
fi

START_EPOCH="$(date +"%s")"

eclipsed() {
  EPOCH="$(date +"%s")"
  echo $((EPOCH - START_EPOCH))
}

BASE="http://localhost:8080/fhir"

DATABASE="$1"
COLUMN_FAMILY="$2"

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "database",
      "valueCode": "$DATABASE"
    },
    {
      "name": "column-family",
      "valueCode": "$COLUMN_FAMILY"
    }
  ]
}
END
}

NOW_EPOCH_SECONDS=$($DATE_CMD +%s)
HEADERS="$(curl -sfH 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' -d "$(parameters)" -o /dev/null -D - "$BASE/\$compact")"
STATUS_URL=$(echo "$HEADERS" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$STATUS_URL")" != "200") ]]; do
  sleep 1
done

JOB_ID=$(echo "$STATUS_URL" | cut -d '/' -f6)
JOB=$(curl -s -H 'Accept: application/fhir+json' "$BASE/__admin/Task/$JOB_ID")

test "profile URL" "$(echo "$JOB" | jq -r '.meta.profile[]')" "https://samply.github.io/blaze/fhir/StructureDefinition/CompactJob"
test "status" "$(echo "$JOB" | jq -r '.status')" "completed"

AUTHORED_ON_ISO=$(echo "$JOB" | jq -r '.authoredOn')
AUTHORED_ON_EPOCH_SECONDS=$($DATE_CMD -d "$AUTHORED_ON_ISO" +%s)
if ((NOW_EPOCH_SECONDS - AUTHORED_ON_EPOCH_SECONDS < 10)); then
  echo "✅ the authoredOn dateTime is set and current"
else
  echo "🆘 the authoredOn dateTime is $AUTHORED_ON_ISO, but should be a current dateTime"
  exit 1
fi

PARAMETER_URI="https://samply.github.io/blaze/fhir/CodeSystem/CompactJobParameter"

input_expr() {
  echo ".input[] | select(.type.coding[] | select(.system == \"$PARAMETER_URI\" and .code == \"$1\"))"
}

test "database" "$(echo "$JOB" | jq -r "$(input_expr "database") | .valueCode")" "$DATABASE"
test "column-family" "$(echo "$JOB" | jq -r "$(input_expr "column-family") | .valueCode")" "$COLUMN_FAMILY"

OUTPUT_URI="https://samply.github.io/blaze/fhir/CodeSystem/CompactJobOutput"

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
