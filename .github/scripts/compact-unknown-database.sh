#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

START_EPOCH="$(date +"%s")"

eclipsed() {
  EPOCH="$(date +"%s")"
  echo $((EPOCH - START_EPOCH))
}

BASE="http://localhost:8080/fhir"

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "database",
      "valueCode": "foo"
    },
    {
      "name": "column-family",
      "valueCode": "bar"
    }
  ]
}
END
}

HEADERS="$(curl -sfH 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' -d "$(parameters)" -o /dev/null -D - "$BASE/\$compact")"
STATUS_URL=$(echo "$HEADERS" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$STATUS_URL")" != "200") ]]; do
  sleep 0.1
done

BUNDLE=$(curl -sH 'Accept: application/fhir+json' "$STATUS_URL")

test "bundle type" "$(echo "$BUNDLE" | jq -r '.type')" "batch-response"
test "response status" "$(echo "$BUNDLE" | jq -r '.entry[0].response.status')" "400"
test "response severity" "$(echo "$BUNDLE" | jq -r '.entry[0].response.outcome.issue[0].severity')" "error"
test "response code" "$(echo "$BUNDLE" | jq -r '.entry[0].response.outcome.issue[0].code')" "invalid"
test "response diagnostics" "$(echo "$BUNDLE" | jq -r '.entry[0].response.outcome.issue[0].diagnostics')" "Unknown database \`foo\`."
