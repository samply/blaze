#!/bin/bash -e

#
# This script first creates a patient and expects the conditional update with
# If-None-Match=* to fail afterwards.
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_ID=$(curl -sH "Content-Type: application/fhir+json" \
  -d '{"resourceType": "Patient"}' "$BASE/Patient" | jq -r .id)

PATIENT="{\"resourceType\": \"Patient\", \"id\": \"$PATIENT_ID\"}"
RESULT=$(curl -sXPUT -H "Content-Type: application/fhir+json" -H "If-None-Match: *" \
  -d "$PATIENT" "$BASE/Patient/$PATIENT_ID")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$RESULT" | jq -r .issue[0].severity)" "error"
test "code" "$(echo "$RESULT" | jq -r .issue[0].code)" "conflict"
test "diagnostics" "$(echo "$RESULT" | jq -r .issue[0].diagnostics)" "Resource \`Patient/$PATIENT_ID\` already exists."
