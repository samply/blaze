#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

BASE="http://localhost:8080/fhir"
RESULT=$(curl -sXDELETE -H "Prefer: return=OperationOutcome" "$BASE/Observation")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$RESULT" | jq -r .issue[0].severity)" "error"
test "code" "$(echo "$RESULT" | jq -r .issue[0].code)" "too-costly"
test "diagnostics" "$(echo "$RESULT" | jq -r .issue[0].diagnostics)" "Conditional delete of all Observations failed because more than 10,000 matches were found."
