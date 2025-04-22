#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient"
      },
      "request": {
        "method": "POST",
        "url": "Patient"
      }
    }
  ]
}
END
}
RESULT=$(curl -sH "Content-Type: application/fhir+json" -H "Prefer: return=representation" -d "$(bundle)" "$BASE")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "Bundle"
test "bundle type" "$(echo "$RESULT" | jq -r .type)" "batch-response"
test "response status" "$(echo "$RESULT" | jq -r .entry[].response.status)" "201"
test "response resource type" "$(echo "$RESULT" | jq -r .entry[].resource.resourceType)" "Patient"
test_regex "response resource ID" "$(echo "$RESULT" | jq -r .entry[].resource.id)" "^[A-Z0-9]{16}$"
