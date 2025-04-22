#!/bin/bash -e

#
# This script creates a patient and tries to retrieve it through a batch request.
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_ID=$(curl -sH "Content-Type: application/fhir+json" \
  -d '{"resourceType": "Patient"}' \
  "$BASE/Patient" | jq -r .id)

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "request": {
        "method": "GET",
        "url": "Patient/$PATIENT_ID"
      }
    }
  ]
}
END
}
RESULT=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" "$BASE")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "Bundle"
test "bundle type" "$(echo "$RESULT" | jq -r .type)" "batch-response"
test "response status" "$(echo "$RESULT" | jq -r .entry[].response.status)" "200"
test "response resource type" "$(echo "$RESULT" | jq -r .entry[].resource.resourceType)" "Patient"
test "response resource ID" "$(echo "$RESULT" | jq -r .entry[].resource.id)" "$PATIENT_ID"
