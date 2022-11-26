#!/bin/bash -e

#
# This script creates and reads a patient in a single transaction.
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_ID="e42a47bb-a371-4cf5-9f17-51e59c1f612a"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "request": {
        "method": "PUT",
        "url": "Patient/$PATIENT_ID"
      },
      "resource": {
        "resourceType": "Patient",
        "id": "$PATIENT_ID"
      }
    },
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
test "bundle type" "$(echo "$RESULT" | jq -r .type)" "transaction-response"
test "response status" "$(echo "$RESULT" | jq -r .entry[1].response.status)" "200"
test "patient id" "$(echo "$RESULT" | jq -r .entry[1].resource.id)" "$PATIENT_ID"
